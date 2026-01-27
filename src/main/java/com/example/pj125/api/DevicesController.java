package com.example.pj125.api;

import com.example.pj125.common.Ack;
import com.example.pj125.common.ApiException;
import com.example.pj125.common.ErrorCode;
import com.example.pj125.device.Device;
import com.example.pj125.device.DeviceGateway;
import com.example.pj125.device.DeviceId;
import com.example.pj125.device.DeviceStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DevicesController {
    private final DeviceGateway deviceManager;

    public DevicesController(DeviceGateway deviceManager) {
        this.deviceManager = deviceManager;
    }

    @GetMapping
    public Ack<Map<DeviceId, DeviceStatus>> getAll() {
        return Ack.ok(deviceManager.statuses());
    }

    @GetMapping("/{deviceId}/status")
    public Ack<DeviceStatus> getStatus(@PathVariable String deviceId) {
        Device d = device(deviceId);
        return Ack.ok(d.status());
    }

    @GetMapping("/{deviceId}/info")
    public Ack<?> getInfo(@PathVariable String deviceId) {
        Device d = device(deviceId);
        return Ack.ok(d.info());
    }

    @PostMapping("/{deviceId}/connection")
    public Ack<?> connect(@PathVariable String deviceId) {
        Device d = device(deviceId);
        ErrorCode rc = d.connect();
        if (rc != ErrorCode.OK) throw new ApiException(rc, "连接失败: " + rc.name());
        return Ack.ok(d.status());
    }

    @DeleteMapping("/{deviceId}/connection")
    public Ack<?> disconnect(@PathVariable String deviceId) {
        Device d = device(deviceId);
        ErrorCode rc = d.disconnect();
        if (rc != ErrorCode.OK) throw new ApiException(rc, "断开失败: " + rc.name());
        return Ack.ok(d.status());
    }

    @PostMapping("/{deviceId}/safe")
    public Ack<?> enterSafe(@PathVariable String deviceId) {
        Device d = device(deviceId);
        ErrorCode rc = d.stop();
        if (rc != ErrorCode.OK) throw new ApiException(rc, "进入SAFE失败: " + rc.name());
        return Ack.ok(d.status());
    }

    private Device device(String deviceId) {
        DeviceId id;
        try {
            id = DeviceId.valueOf(deviceId.toUpperCase());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "deviceId只能为MAIN或RELAY");
        }
        Device d = deviceManager.get(id);
        if (d == null) throw new ApiException(ErrorCode.NOT_FOUND, "设备不存在: " + deviceId);
        return d;
    }
}
