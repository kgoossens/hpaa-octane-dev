/*
 * © Copyright 2013 EntIT Software LLC
 *  Certain versions of software and/or documents (“Material”) accessible here may contain branding from
 *  Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 *  the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 *  and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 *  marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * Copyright (c) 2018 Micro Focus Company, L.P.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ___________________________________________________________________
 *
 */

package com.hpe.application.automation.tools.octane.tests;

import com.google.inject.Inject;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.api.TestsService;
import com.hp.octane.integrations.dto.configuration.OctaneConfiguration;
import com.hp.octane.integrations.dto.connectivity.OctaneResponse;
import com.hpe.application.automation.tools.octane.tests.build.BuildHandlerUtils;
import com.hp.mqm.client.exception.FileNotFoundException;
import com.hpe.application.automation.tools.octane.ResultQueue;
import com.hpe.application.automation.tools.octane.client.RetryModel;
import com.hpe.application.automation.tools.octane.configuration.ConfigurationService;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.util.TimeUnit2;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Date;

@Extension(dynamicLoadable = YesNoMaybe.NO)
public class TestDispatcher extends AbstractSafeLoggingAsyncPeriodWork {
	private static Logger logger = LogManager.getLogger(TestDispatcher.class);

	static final String TEST_AUDIT_FILE = "mqmTests_audit.json";

	@Inject
	private RetryModel retryModel;

	private ResultQueue queue;

	public TestDispatcher() {
		super("MQM Test Dispatcher");
	}

	@Override
	protected void doExecute(TaskListener listener) {
		if (queue.peekFirst() == null) {
			return;
		}
		if (retryModel.isQuietPeriod()) {
			logger.info("there are pending test results, but we are in quiet period");
			return;
		}
		Jenkins jenkinsInstance = Jenkins.getInstance();
		if (jenkinsInstance == null) {
			logger.error("can't obtain Jenkins instance - major failure, test dispatching won't run");
			return;
		}

		TestsService testsService = OctaneSDK.getInstance().getTestsService();
		OctaneConfiguration configuration = OctaneSDK.getInstance().getPluginServices().getOctaneConfiguration();
		ResultQueue.QueueItem item;

		//  iterate and dispatch all the pending test results
		while ((item = queue.peekFirst()) != null) {
			Job job = (Job) jenkinsInstance.getItemByFullName(item.getProjectName());
			if (job == null) {
				logger.warn("job '" + item.getProjectName() + "' no longer exists, its test results won't be pushed to Octane");
				queue.remove();
				continue;
			}

			Run run = job.getBuildByNumber(item.getBuildNumber());
			if (run == null) {
				logger.warn("build '" + item.getProjectName() + " #" + item.getBuildNumber() + "' no longer exists, its test results won't be pushed to Octane");
				queue.remove();
				continue;
			}

			File resultFile;
			try {
				resultFile = new File(run.getRootDir(), TestListener.TEST_RESULT_FILE);
			} catch (FileNotFoundException e) {
				logger.error("'" + TestListener.TEST_RESULT_FILE + "' file no longer exists, test results of '" + item.getProjectName() + " #" + item.getBuildNumber() + "' won't be pushed to Octane");
				queue.remove();
				continue;
			}

			try {
				boolean needTestResult = testsService.isTestsResultRelevant(ConfigurationService.getModel().getIdentity(), BuildHandlerUtils.getJobCiId(run));
				if (!needTestResult) {
					logger.info("test results of '" + item.getProjectName() + " #" + item.getBuildNumber() + "' are NOT needed, won't be pushed to Octane");
					queue.remove();
					continue;
				}
			} catch (IOException ioe) {
				logger.error("pre-flight request failed - server temporarily unavailable, will retry later", ioe);
				retryModel.failure();
				break;
			}

			try {
				String testsPushRequestId;
				OctaneResponse response = testsService.pushTestsResult(new FileInputStream(resultFile));
				testsPushRequestId = response.getBody();
				if (response.getStatus() == 200 && testsPushRequestId != null && !testsPushRequestId.isEmpty()) {
					logger.info("successfully pushed test results of '" + item.getProjectName() + " #" + item.getBuildNumber() + "'");
					audit(configuration, run, testsPushRequestId, false);
					queue.remove();
				} else if (response.getStatus() == 503) {
					logger.error("server temporarily unavailable, will retry later");
					audit(configuration, run, null, true);
					retryModel.failure();
					break;
				} else {
					logger.error("unexpected result while pushing test results of '" + item.getProjectName() + " #" + item.getBuildNumber() + "': " +
							response.getStatus() + " - " + response.getBody());
					audit(configuration, run, null, false);
					queue.remove();
				}
			} catch (IOException ioe) {
				logger.error("push test results failed - server temporarily unavailable, will retry later", ioe);
				audit(configuration, run, null, true);
				retryModel.failure();
				break;
			}
		}
	}

	private void audit(OctaneConfiguration octaneConfiguration, Run run, String id, boolean temporarilyUnavailable) {
		try {
			FilePath auditFile = new FilePath(new File(run.getRootDir(), TEST_AUDIT_FILE));
			JSONArray audit;
			if (auditFile.exists()) {
				InputStream is = auditFile.read();
				audit = JSONArray.fromObject(IOUtils.toString(is, "UTF-8"));
				IOUtils.closeQuietly(is);
			} else {
				audit = new JSONArray();
			}
			JSONObject event = new JSONObject();
			event.put("id", id);
			event.put("pushed", id != null && !id.isEmpty());
			event.put("date", DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.format(new Date()));
			event.put("location", octaneConfiguration.getUrl());
			event.put("sharedSpace", octaneConfiguration.getSharedSpace());
			if (temporarilyUnavailable) {
				event.put("temporarilyUnavailable", true);
			}
			audit.add(event);
			auditFile.write(audit.toString(), "UTF-8");
		} catch (IOException | InterruptedException e) {
			logger.error("failed to create audit entry for  " + octaneConfiguration + "; " + run);
		}
	}

	@Override
	public long getRecurrencePeriod() {
		String value = System.getProperty("Octane.TestDispatcher.Period");
		if (!StringUtils.isEmpty(value)) {
			return Long.valueOf(value);
		}
		return TimeUnit2.SECONDS.toMillis(10);
	}

	@Inject
	public void setTestResultQueue(TestsResultQueue queue) {
		this.queue = queue;
	}

	void _setTestResultQueue(ResultQueue queue) {
		this.queue = queue;
	}

	void _setRetryModel(RetryModel retryModel) {
		this.retryModel = retryModel;
	}
}
