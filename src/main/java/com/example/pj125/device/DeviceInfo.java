package com.example.pj125.device;

public class DeviceInfo {
    private DeviceId deviceId;
    private String model;
    // Spec fields
    private String serialNumber;
    private String firmwareVersion;
    private String protocolVersion;

    public DeviceId getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(DeviceId deviceId) {
        this.deviceId = deviceId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Backward compatible field name: serial (old) -> serialNumber (spec).
     */
    public String getSerial() {
        return serialNumber;
    }

    public void setSerial(String serial) {
        this.serialNumber = serial;
    }

    /**
     * Backward compatible field name: version (old) -> firmwareVersion (spec).
     */
    public String getVersion() {
        return firmwareVersion;
    }

    public void setVersion(String version) {
        this.firmwareVersion = version;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
}
