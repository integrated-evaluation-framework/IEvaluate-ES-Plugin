package edu.mayo.dhs.ievaluate.plugins.es.config;

import java.util.Objects;

public class ESPluginConfig {
    private String httpSchema;
    private String hostName;
    private int port;
    private String user;
    private String pass;
    private String applicationIndex;
    private String baselineIndex;
    private String errorIndex;
    private String userIndex;

    public String getHttpSchema() {
        return httpSchema;
    }

    public void setHttpSchema(String httpSchema) {
        this.httpSchema = httpSchema;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getApplicationIndex() {
        return applicationIndex;
    }

    public void setApplicationIndex(String applicationIndex) {
        this.applicationIndex = applicationIndex;
    }

    public String getBaselineIndex() {
        return baselineIndex;
    }

    public void setBaselineIndex(String baselineIndex) {
        this.baselineIndex = baselineIndex;
    }

    public String getErrorIndex() {
        return errorIndex;
    }

    public void setErrorIndex(String errorIndex) {
        this.errorIndex = errorIndex;
    }

    public String getUserIndex() {
        return userIndex;
    }

    public void setUserIndex(String userIndex) {
        this.userIndex = userIndex;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void checkValid() {
        // Force a throw of an exception via requireNonNull if missing config values
        Objects.requireNonNull(httpSchema);
        Objects.requireNonNull(hostName);
        Objects.requireNonNull(user);
        Objects.requireNonNull(pass);
        Objects.requireNonNull(applicationIndex);
        Objects.requireNonNull(baselineIndex);
        Objects.requireNonNull(errorIndex);
        Objects.requireNonNull(userIndex);
    }
}
