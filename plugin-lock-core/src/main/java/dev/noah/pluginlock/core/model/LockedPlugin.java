package dev.noah.pluginlock.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class LockedPlugin {
    private String id;
    private String name;
    private String provider;
    private String projectId;
    private String versionId;
    private String versionName;
    private String fileName;
    private String downloadUrl;
    private String sha512;
    private String sha256;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String compatibilityWarning;
    private long size;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getSha512() {
        return sha512;
    }

    public void setSha512(String sha512) {
        this.sha512 = sha512;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getCompatibilityWarning() {
        return compatibilityWarning;
    }

    public void setCompatibilityWarning(String compatibilityWarning) {
        this.compatibilityWarning = compatibilityWarning;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
