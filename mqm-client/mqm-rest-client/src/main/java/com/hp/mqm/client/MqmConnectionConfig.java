package com.hp.mqm.client;

public class MqmConnectionConfig {

    private String location;
    private String domain;
    private String project;
    private String username;
    private String password;
    private String clientType;

    private String proxyHost;
    private Integer proxyPort;
    private ProxyCredentials proxyCredentials;
    private Integer defaultSocketTimeout;
    private Integer defaultConnectionTimeout;
    private Integer defaultConnectionRequestTimeout;

    public MqmConnectionConfig(String location, String domain, String project, String username, String password, String clientType) {
        this.location = location;
        this.domain = domain;
        this.project = project;
        this.username = username;
        this.password = password;
        this.clientType = clientType;
    }

    MqmConnectionConfig(String location, String domain, String project, String username, String password, String clientType, String proxyHost, Integer proxyPort) {
        this.location = location;
        this.domain = domain;
        this.project = project;
        this.username = username;
        this.password = password;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.clientType = clientType;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getLocation() {
        return location;
    }

    public String getDomain() {
        return domain;
    }

    public String getProject() {
        return project;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public Integer getDefaultSocketTimeout() {
        return defaultSocketTimeout;
    }

    public void setDefaultSocketTimeout(Integer defaultSocketTimeout) {
        this.defaultSocketTimeout = defaultSocketTimeout;
    }

    public Integer getDefaultConnectionTimeout() {
        return defaultConnectionTimeout;
    }

    public void setDefaultConnectionTimeout(Integer defaultConnectionTimeout) {
        this.defaultConnectionTimeout = defaultConnectionTimeout;
    }

    public Integer getDefaultConnectionRequestTimeout() {
        return defaultConnectionRequestTimeout;
    }

    public void setDefaultConnectionRequestTimeout(Integer defaultConnectionRequestTimeout) {
        this.defaultConnectionRequestTimeout = defaultConnectionRequestTimeout;
    }

    public ProxyCredentials getProxyCredentials() {
        return proxyCredentials;
    }

    public void setProxyCredentials(ProxyCredentials proxyCredentials) {
        this.proxyCredentials = proxyCredentials;
    }
}
