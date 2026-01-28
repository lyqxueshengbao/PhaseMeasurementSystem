# 设备 Agent（HTTP）接口约定（预留）

目标：未来主站（MAIN）与转发站（RELAY）各自运行一个本地 **Device Agent**（靠近 FPGA/总控），上位机（本项目 Spring Boot 服务）通过网络分别访问两端 Agent，实现“同一套编排/落盘/SSE 口径不变”，仅替换 `device` 的 infra 实现。

> 当前版本远程实现仍是占位（`RemoteDevicePlaceholder`），本文档用于提前对齐“将来要发什么控制字 / 收什么回读与状态”。

## 部署形态（推荐）

- MAIN 端：运行 `pj125-device-agent`（HTTP），服务在 `http://<main-host>:<port>`
- RELAY 端：运行 `pj125-device-agent`（HTTP），服务在 `http://<relay-host>:<port>`
- 上位机：配置并分别连接两端（不会要求 MAIN/RELAY 两端互联）

本项目侧配置（示例）：

```yaml
pj125:
  devices:
    main:
      endpoint: http://10.0.1.10:18080
    relay:
      endpoint: http://10.0.2.10:18080
```

`local-sim` 仍是默认验收路径（进程内模拟器）。

## 统一返回包（建议复用 Ack）

为减少上位机侧改动，建议 Agent 复用本项目的通用返回包结构：

```json
{
  "success": true,
  "code": "OK",
  "message": "成功",
  "data": {},
  "ts": "2026-01-28T00:00:00+08:00"
}
```

- `success=false` 时，`code` 建议使用本项目 `ErrorCode` 的枚举名（例如 `DEVICE_OFFLINE/DEVICE_BUSY/APPLY_FAILED/...`）。
- HTTP 状态码建议：**正常业务错误也返回 HTTP 200**（用 `success/code` 表达），只有协议级错误（比如 JSON 非法）才用 4xx/5xx。

## 数据模型（与上位机对齐）

### DeviceConfig（控制字/配置）

与本项目 `DeviceConfig` 字段一致：

```json
{
  "txEnable": true,
  "ddsFreqHz": 10000000.0,
  "referencePathDelayNs": 120.0,
  "measurePathDelayNs": 180.0
}
```

- `ddsFreqHz`：DDS 频率控制字（单位 Hz）。**仅 MAIN 有效**；RELAY 允许出现但应忽略且不报错。

### DeviceStatus（状态/心跳）

与本项目 `DeviceStatus` 字段一致（Agent 可按能力返回子集，但建议字段齐全）：

```json
{
  "deviceId": "MAIN",
  "opState": "IDLE",
  "lockState": "UNLOCKED",
  "connected": true,
  "version": "fpga-1.0.0",
  "temperatureC": 40.0,
  "alarms": [],
  "lastUpdatedTs": "2026-01-28T00:00:00+08:00",
  "lastErrorCode": null,
  "lastErrorMessage": null,
  "rttMs": 3
}
```

- `rttMs`：上位机侧或 Agent 侧测得的往返耗时（ms）。本项目当前实现是上位机测量（调用 `ping/status` 的耗时）。

### DeviceInfo（静态信息）

与本项目 `DeviceInfo` 对齐：`model/serialNumber/firmwareVersion/protocolVersion` 等。

## Agent API 列表（建议）

以下路径以“单设备 Agent”为前提（MAIN/RELAY 各起一套服务，因此不需要 `{deviceId}` 路径参数）。

### 1) 心跳/状态

- `GET /api/device/ping` → `Ack<DeviceStatus>`
  - 轻量探测（可只做连通性/版本号），建议尽量快
- `GET /api/device/status` → `Ack<DeviceStatus>`
  - 返回完整状态（可更慢）

### 2) 设备信息

- `GET /api/device/info` → `Ack<DeviceInfo>`

### 3) 连接管理

- `POST /api/device/connection` → `Ack<DeviceStatus>`（connect）
- `DELETE /api/device/connection` → `Ack<DeviceStatus>`（disconnect）

### 4) 下发配置（控制字）

- `POST /api/device/config`（body=`DeviceConfig`）→ `Ack<DeviceStatus>`
  - 语义：写入缓冲区，不一定立刻生效
- `POST /api/device/apply` → `Ack<DeviceStatus>`
  - 语义：使缓冲配置生效（可能有延迟）
- `GET /api/device/readbackConfig` → `Ack<DeviceConfig>`
  - 语义：回读当前“已生效配置”（用于验收可回读）

### 5) 流程控制

- `POST /api/device/lock` → `Ack<DeviceStatus>`（startLock）
- `POST /api/device/measurement` → `Ack<DeviceStatus>`（startMeasurement）
- `POST /api/device/safe` → `Ack<DeviceStatus>`（stop / enter safe）

## 与上位机编排步骤的映射

上位机（本项目）编排逻辑大致按如下调用序列：

- `CHECK_DEVICES`：`ping/status`，必要时 `connect`
- `APPLY_RECIPE`：`POST config` → `POST apply` → `GET readbackConfig`（写入 `run_info.json` 的 `mainAppliedConfig/relayAppliedConfig`）
- `LOCK_START/WAIT_LOCKED`：`POST lock` → 轮询 `status` 直到 `LOCKED`
- `MEASURE`：`POST measurement` → 轮询 `status` 直到非 `BUSY`

测量结果的产生方式（由 FPGA/Agent 返回“结果”还是上位机本地 DSP 计算）属于下一阶段范围；本接口先对齐“控制字 + 状态/回读”的最小闭环。

