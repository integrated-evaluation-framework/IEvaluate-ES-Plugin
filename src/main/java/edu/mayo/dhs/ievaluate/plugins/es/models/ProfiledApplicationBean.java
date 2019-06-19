package edu.mayo.dhs.ievaluate.plugins.es.models;

/**
 * Used internally for ES storage of applications
 */
public class ProfiledApplicationBean {
    private String classType;
    private String appId;
    private String value; // Because applications can have a lot of different serialized json types, we store as a flat string to avoid mapping issues

    public ProfiledApplicationBean() {}

    public ProfiledApplicationBean(String classType, String value, String appId) {
        this.classType = classType;
        this.value = value;
        this.appId = appId;
    }

    public String getClassType() {
        return classType;
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }
}
