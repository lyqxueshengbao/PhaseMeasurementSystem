# 比相/时延测量上位机（模拟器版）可执行规格说明（最终验收口径）

> 目标：在“真机未完成”的阶段，用纯软件模拟器跑通上位机业务流程，固化接口/用例/落盘格式作为外包验收指标。  
> 约束：Maven + Spring Boot（端口8080），无数据库（仅文件落盘到`./data`），Web UI必须中文。  
> 模拟器范围：不模拟混频器/滤波器等射频细节，只需在系统级输出“相位/时延”测量结果，保证流程与接口可验收。

---

## 1) 分层架构与包结构（建议）

### 1.1 分层架构（Domain / Infra / App / Web）
- **domain（领域层）**
  - 定义：领域模型、枚举、接口（Device、MeasurementEngine、LinkModel、RunOrchestrator等）、错误码与统一响应结构
  - 规则：不依赖Spring、不依赖IO/网络/文件；只包含“业务口径”
- **infra（基础设施层）**
  - 定义：模拟器实现（SimulatedMainStation/Relay）、链路模型实现、落盘存储实现、SSE事件总线实现
  - 规则：可依赖Spring/IO；实现domain接口；与外部世界交互
- **app（应用层/用例层）**
  - 定义：RunOrchestrator流程编排、Recipe服务、Run服务（启动/查询/回放）
  - 规则：依赖domain接口，组合infra实现，提供“可执行用例”
- **web（交互层）**
  - 定义：REST API、SSE端点、中文页面（UI路由与静态资源）
  - 规则：只做入参校验、调用app层、封装返回；不承载业务规则

### 1.2 包结构（示例）
- `com.example.pj125.domain`
  - `device`：设备抽象（Device、MainStation/RelayStation可选子接口）、DeviceInfo/Status/Config
  - `measurement`：MeasurementMode/Request/Result、MeasurementPlan、质量指标口径
  - `link`：LinkModel配置/接口
  - `recipe`：Recipe模型
  - `run`：Run/RunStatus/RunStep/RunInfo、落盘文件模型
  - `common`：ErrorCode、Ack/ApiResponse
- `com.example.pj125.infra`
  - `device.sim`：模拟主站/转发站实现、故障注入
  - `link.sim`：模拟链路模型
  - `measurement.sim`：可复现测量引擎
  - `storage.fs`：文件落盘实现（Recipe/Run）
  - `sse`：SSE事件总线（发布/订阅/回放）
- `com.example.pj125.app`
  - `orchestrator`：RunOrchestrator状态机/步骤流
  - `service`：RecipeService、RunService、DeviceService（可选）
- `com.example.pj125.web`
  - `api`：REST Controller
  - `sse`：SSE Controller
  - `ui`：页面Controller（Thymeleaf）或纯静态
  - `static/templates`：静态资源/模板（中文UI）

---

## 2) 设备抽象接口设计（最小集合）

### 2.1 Device接口 vs 两个子接口
- **推荐：一个`Device`接口 + `deviceId/deviceType`区分主站/转发站**
  - 原因：主站与转发站黑盒能力集合一致（连接/配置/锁定/测量/SAFE），后续真机实现替换成本更低
  - 保留扩展：若未来主站/转发站能力差异明显，再引入`MainStation extends Device`、`RelayStation extends Device`作为“可选扩展”，不破坏上层流程

### 2.2 方法列表与语义（建议签名层面）
> 所有方法建议返回统一结构：`Result<T>`或`Ack<T>`（包含`errorCode`+中文原因），并带超时建议与典型错误码。

1) `connect()`
- 语义：建立与设备通信（模拟器为状态切换；真机可能为TCP握手/初始化）
- 成功：设备进入“已连接可配置”态
- 典型错误码：`DEVICE_OFFLINE`、`TIMEOUT`、`PROTOCOL_ERROR`
- 超时建议：2s~5s（真机可更长），可重试2次（间隔500ms）

2) `disconnect()`
- 语义：断开通信并释放资源
- 成功：状态变为`OFFLINE`
- 错误码：通常返回`OK`（即便已断开也可幂等）
- 超时：1s

3) `getDeviceInfo() : DeviceInfo`
- 语义：读取设备型号/序列号/版本/协议版本/能力集
- 错误码：`DEVICE_OFFLINE`
- 超时：1s~2s

4) `getStatus() : DeviceStatus`
- 语义：读取设备实时状态（连接/锁定/忙/温度/告警）
- 错误码：`DEVICE_OFFLINE`
- 超时：0.5s~1s（高频轮询）

5) `setConfig(DeviceConfig config)`
- 语义：写入配置缓冲区（不立即生效）
- 错误码：`DEVICE_OFFLINE`、`DEVICE_BUSY`、`VALIDATION_ERROR`
- 超时：1s~3s

6) `apply()`
- 语义：使配置生效；设备可能短暂进入busy；之后进入“等待锁定/可测量”
- 错误码：`DEVICE_BUSY`、`APPLY_FAILED`、`TIMEOUT`
- 超时建议：模拟器由`applyDelayMs`决定；真机建议5s~30s
- 重试：通常不自动重试（需排查配置或设备）

7) `readbackConfig() : DeviceConfig`
- 语义：读取设备当前生效配置（用于验收/追溯）
- 错误码：`DEVICE_OFFLINE`
- 超时：1s~3s

8) `enterSafeMode()`（或`stop()`）
- 语义：进入安全态（停止发射/停止采集/停止测量；用于失败兜底）
- 错误码：尽量幂等；失败返回`SAFE_FAILED`
- 超时：2s~5s

9) `startMeasurement(MeasurementRequest req)`
- 语义：启动一次测量动作（设备可能进入BUSY）
- 错误码：`DEVICE_OFFLINE`、`DEVICE_BUSY`、`NOT_LOCKED`、`MEASUREMENT_START_FAILED`
- 超时：由`measurementTimeMs`决定；真机建议5s~60s

10) `getMeasurementResult() : MeasurementResult`（或按run/seq获取）
- 语义：拉取本次测量结果（相位/时延/质量指标）
- 错误码：`NO_RESULT`、`TIMEOUT`、`MEASUREMENT_FAILED`
- 超时：1s~3s（若结果需等待，建议配合轮询/阻塞超时）

（可选占位，建议保留以兼容未来验收扩展）
- `startCapture(CaptureConfig)` / `stopCapture()`
  - 当前模拟器可不实现真实采集，但保留接口占位与状态变化，避免未来UI/流程大改

---

## 3) 核心数据模型字段定义（含JSON示例，含单位）

> 统一约束：所有时间戳用ISO-8601；单位必须写在字段名或schema说明中。

### 3.1 DeviceInfo
字段（规范）：
- `deviceId`: `"MAIN"|"RELAY"`
- `model`: string
- `serialNumber`: string
- `firmwareVersion`: string
- `protocolVersion`: string
- `capabilities`: object（可扩展）

示例（规范）：
```json
{
  "deviceId": "MAIN",
  "model": "SimulatedMainStation",
  "serialNumber": "SIM-MAIN-001",
  "firmwareVersion": "sim-1.0.0",
  "protocolVersion": "1.0",
  "capabilities": {
    "supportsCapture": false,
    "supportedModes": ["LINK", "MAIN_INTERNAL", "RELAY_INTERNAL"]
  }
}
```

### 3.2 DeviceStatus
字段（规范）：
- `deviceId`: `"MAIN"|"RELAY"`
- `connected`: boolean
- `opState`: `OFFLINE|IDLE|READY|BUSY|ERROR`
- `lockState`: `UNLOCKED|LOCKING|LOCKED|LOST`
- `temperatureC`: number（单位：摄氏度）
- `alarms`: string[]（中文告警）
- `lastUpdatedTs`: string（ISO-8601）
- `lastErrorCode`: string（可空）
- `lastErrorMessage`: string（中文，可空）

示例（规范）：
```json
{
  "deviceId": "RELAY",
  "connected": true,
  "opState": "READY",
  "lockState": "LOCKED",
  "temperatureC": 41.2,
  "alarms": [],
  "lastUpdatedTs": "2026-01-25T10:00:01.123+08:00",
  "lastErrorCode": null,
  "lastErrorMessage": null
}
```

### 3.3 DeviceConfig
字段（规范：示例 + 可扩展字典）：
- `workFreqHz`: number（Hz，工作频点）
- `gainDb`: number（dB）
- `routeId`: string（路由选择）
- `captureLengthSamples`: integer（采集长度，samples）
- `txEnable`: boolean（发射开关）
- `params`: object（可扩展配置字典，键值对）

示例（规范）：
```json
{
  "workFreqHz": 10000000,
  "gainDb": 12.5,
  "routeId": "R1",
  "captureLengthSamples": 1048576,
  "txEnable": true,
  "params": {
    "refPathDelayNs": 120.0,
    "measPathDelayNs": 180.0,
    "note": "扩展字段，模拟器可忽略"
  }
}
```

### 3.4 LinkModel
字段（规范）：
- `fixedLinkDelayNs`: number（ns）
- `driftPpm`: number（ppm，漂移强度）
- `noiseStdNs`: number（ns，噪声标准差）
- `basePhaseDeg`: number（deg，基准相位）
- `modelVersion`: string（可选）

示例（规范）：
```json
{
  "modelVersion": "sim-link-1",
  "fixedLinkDelayNs": 800.0,
  "driftPpm": 0.2,
  "noiseStdNs": 0.5,
  "basePhaseDeg": 15.0
}
```

### 3.5 MeasurementMode
枚举（规范）：
- `LINK`：链路时延/比相测量（主站↔转发站链路引入的相位差/时延差）
- `MAIN_INTERNAL`：主站内部参考路径 vs 测量路径
- `RELAY_INTERNAL`：转发站内部参考路径 vs 测量路径
- `ATMOSPHERIC_OUTPUT`：大气时延输出（派生结果，不直接对设备发起）

### 3.6 MeasurementRequest
字段（规范）：
- `mode`: MeasurementMode
- `repeatIndex`: int（便于追溯；也可由上层编排赋值）
- `capture`: object（可选，占位）
- `needCalibration`: boolean（可选；模拟器可忽略但应落盘）

示例（规范）：
```json
{
  "mode": "LINK",
  "repeatIndex": 0,
  "needCalibration": true,
  "capture": {
    "enabled": false,
    "lengthSamples": 1048576
  }
}
```

### 3.7 MeasurementResult
字段（规范，单位硬要求）：
- `ts`: string（ISO-8601）
- `mode`: MeasurementMode
- `repeatIndex`: int
- `delayNs`: number（ns）
- `phaseDeg`: number（deg，建议范围-180~180）
- `confidence`: number（0~1）
- `qualityFlag`: `OK|WARN|BAD|INVALID`
- 可选：`snrDb`、`flags`（中文）、`explain`（用于可解释/可复现）

精度口径（验收口径，避免“阶段/舍入”导致ps级信息丢失）：
- 所有时延相关数值（如 `delayNs`，以及其他以 `Ns` 结尾的 ns 字段）后端内部必须使用 **IEEE-754 64位浮点（double）或等价精度** 存储与计算，不得使用 float。
- 落盘与API返回的数值不得舍入到 **0.001ns（1ps）** 以上的粒度（允许更高精度）。
- 不要求JSON数字输出必须包含固定小数位（例如不要求 `95.0000`）；UI展示可统一格式化到 4 位小数（ns），但不作为验收硬要求。

示例（规范）：
```json
{
  "ts": "2026-01-25T10:00:05.200+08:00",
  "mode": "LINK",
  "repeatIndex": 0,
  "delayNs": 801.23,
  "phaseDeg": 16.02,
  "confidence": 0.93,
  "qualityFlag": "OK",
  "snrDb": 28.5,
  "flags": [],
  "explain": {
    "seedKey": "RUN-...|RCP-001|LINK|0",
    "model": "fixed+drift+noise"
  }
}
```

### 3.8 AtmosphericDelayResult
字段（规范）：
- `ts`
- `formulaVersion`: string（例如`atm-v1`）
- `status`: `SUCCEEDED|FAILED`
- `atmosphericDelayNs`: number|null（ns；失败可为null）
- `uncertaintyNs`: number|null（ns；失败可为null）
- `inputsSnapshot`: object（三项均值/方差/有效条数、门限判断）
- `error`: object|null（失败原因）

示例（规范，成功）：
```json
{
  "ts": "2026-01-25T10:00:08.000+08:00",
  "formulaVersion": "atm-v1",
  "status": "SUCCEEDED",
  "atmosphericDelayNs": 706.11,
  "uncertaintyNs": 1.20,
  "inputsSnapshot": {
    "link": {"avgNs": 801.23, "stdNs": 0.60, "validCount": 8},
    "mainInternal": {"avgNs": 60.10, "stdNs": 0.30, "validCount": 8},
    "relayInternal": {"avgNs": 35.02, "stdNs": 0.25, "validCount": 8},
    "minValidRequired": 6
  },
  "error": null
}
```

示例（规范，失败：缺项/有效条数不足）：
```json
{
  "ts": "2026-01-25T10:00:08.000+08:00",
  "formulaVersion": "atm-v1",
  "status": "FAILED",
  "atmosphericDelayNs": null,
  "uncertaintyNs": null,
  "inputsSnapshot": {
    "missingModes": ["MAIN_INTERNAL"],
    "minValidRequired": 6
  },
  "error": {"errorCode":"ATMOSPHERIC_FAILED","message":"缺少测量项: MAIN_INTERNAL"}
}
```

### 3.9 Recipe
字段（规范）：
- `recipeId`: string
- `name`: string（中文）
- `mainConfig`: DeviceConfig
- `relayConfig`: DeviceConfig
- `linkModel`: LinkModel
- `measurementPlan`:
  - `modes`: MeasurementMode[]（按顺序执行；不含`ATMOSPHERIC_OUTPUT`亦可）
  - `repeat`: int
- 可选：`simulatorProfile`（故障注入与延迟参数覆盖）

### 3.10 Run / RunStatus
- `Run`
  - `runId`: string
  - `recipeId`: string
  - `startedAt`: string
  - `endedAt`: string|null
  - `status`: RunStatus
  - `step`: string（见步骤定义）
  - `error`: object|null
- `RunStatus`（规范）：`RUNNING|SUCCEEDED|FAILED|CANCELLED|SAFE`

示例（规范）：
```json
{
  "runId": "RUN-20260125-100001-001",
  "recipeId": "RCP-001",
  "startedAt": "2026-01-25T10:00:01.000+08:00",
  "endedAt": null,
  "status": "RUNNING",
  "step": "WAIT_LOCKED",
  "error": null
}
```

### 3.11 ErrorCode（示例表，需中英文含义）
- `OK`：成功
- `VALIDATION_ERROR`：参数校验失败
- `NOT_FOUND`：资源不存在
- `DEVICE_OFFLINE`：设备离线/未连接
- `DEVICE_BUSY`：设备忙
- `DEVICE_ERROR`：设备错误态
- `LOCK_TIMEOUT`：等待锁定超时
- `LOCK_LOST`：运行中失锁
- `APPLY_FAILED`：配置生效失败
- `MEASUREMENT_FAILED`：测量失败
- `ATMOSPHERIC_FAILED`：大气时延计算失败
- `PERSIST_FAILED`：落盘失败
- `INTERNAL_ERROR`：内部错误

### 3.12 Ack / ApiResponse（统一响应结构）
字段（规范）：
- `success`: boolean
- `code`: ErrorCode
- `message`: string（中文）
- `data`: any
- `ts`: ISO-8601

示例（规范）：
```json
{
  "success": true,
  "code": "OK",
  "message": "成功",
  "data": { "runId": "RUN-...", "sseUrl": "/api/sse/runs/RUN-..." },
  "ts": "2026-01-25T10:00:01.050+08:00"
}
```

### 3.x 现有实现映射（字段对齐）

#### 3.x.1 MeasurementResult字段名
- `delayNs/phaseDeg/confidence/qualityFlag`：与规范一致 ✅

#### 3.x.2 MeasurementMode枚举值
- 现有实现：`LINK`, `MAIN_INTERNAL`, `RELAY_INTERNAL` ✅
- 规范：同上 ✅

#### 3.x.3 DeviceInfo字段
- 现有实现（示例字段）：`deviceId`, `model`, `serial`, `version`
- 规范字段：`deviceId`, `model`, `serialNumber`, `firmwareVersion`, `protocolVersion`
- 映射（实现 → 规范）：

| 实现字段 | 规范字段 | 说明 | 统一策略建议 |
|---|---|---|---|
| `serial` | `serialNumber` | 字段名差异 | 建议新增规范字段并保留旧字段兼容 |
| `version` | `firmwareVersion` | 字段名差异 | 建议新增规范字段并保留旧字段兼容 |
| （无） | `protocolVersion` | 缺失 | 建议补齐为常量或由真机协议上报 |

#### 3.x.4 Run状态字段
- 现有实现：`RunInfo.state = RUNNING|DONE|FAILED`，`RunInfo.step = INIT|...|DONE|FAILED`
- 规范：`Run.status = RUNNING|SUCCEEDED|FAILED|CANCELLED|SAFE`
- 映射（实现 → 规范）：

| 实现值 | 规范值 | 说明 | 统一策略建议 |
|---|---|---|---|
| `DONE` | `SUCCEEDED` | 语义一致 | 建议对外API增加`status`并映射`DONE->SUCCEEDED`，保留`state`兼容UI |
| （无） | `CANCELLED` | 缺失 | 建议预留取消接口与落盘口径 |
| （无） | `SAFE` | 缺失 | 可作为FAILED后的设备状态而非run状态；或扩展run状态 |

---

## 4) 一键测量流程 RunOrchestrator（状态机/步骤流）

> 状态机步骤建议：`INIT -> CHECK_DEVICES -> APPLY_RECIPE -> LOCK_START -> WAIT_LOCKED -> MEASURE -> SUMMARY -> PERSIST -> DONE`  
> 失败进入：`FAILED`；取消进入：`CANCELLED`（预留）；两者都必须强制 `enterSafeMode()` 并生成 `error.json`（方案B验收口径）。

### 步骤说明（做什么 → 等待条件 → 超时与重试 → 失败如何进入SAFE → 输出什么）
1) **INIT**
- 做什么：生成`runId`；创建run目录；写`run_info.json`初始状态；写入`recipe.json`与`device_info.json`；初始化`measurement_result.json`（允许空数组但文件必须存在）
- 输出：SSE `STEP/LOG`

2) **CHECK_DEVICES（连接两设备）**
- 做什么：对主站/转发站执行`connect()`；读取`DeviceStatus`
- 等待条件：两台设备`connected=true`
- 超时与重试：connect可重试2次；总超时5s~15s
- 失败处理（方案B写死）：
  - run进入FAILED（或CANCELLED）
  - 必须进入SAFE并写`error.json`（必需文件）
  - 必须保证 `GET /api/runs/{runId}/atmospheric_delay` 可返回失败原因（写死：返回统一`ApiResponse`，其`data`为`error.json`内容）
- 输出：SSE `DEVICE_STATUS`、`LOG`

3) **检查参考锁定（LOCKED）**
- 做什么：读取状态；若已LOCKED可跳过等待
- 失败处理：按LOCK_TIMEOUT/NOT_LOCKED/LOCK_LOST（同方案B：FAILED→SAFE→error.json + /atmospheric_delay可读到失败原因）

4) **APPLY_RECIPE（下发配置 + Apply）**
- 做什么：`setConfig(mainConfig/relayConfig)`→`apply()`→`readbackConfig()`（建议）
- 等待条件：apply完成
- 超时：apply建议30s（模拟器按`applyDelayMs`）
- 失败处理（方案B写死）：FAILED/CANCELLED→SAFE→写`error.json`；并保证`/atmospheric_delay`可读到失败原因（`ApiResponse.data`为`error.json`内容）
- 输出：SSE `LOG`

5) **LOCK_START**
- 做什么：触发设备进入LOCKING（真机可能在apply后自动锁定；保留显式动作用于验收）
- 等待条件：`lockState=LOCKING`（至少出现一次）
- 失败处理（方案B写死）：FAILED/CANCELLED→SAFE→写`error.json`；并保证`/atmospheric_delay`可读到失败原因（`ApiResponse.data`为`error.json`内容）
- 输出：SSE `DEVICE_STATUS`、`LOG`

6) **WAIT_LOCKED（等待READY/LOCKED）**
- 做什么：轮询两设备状态
- 等待条件：两台设备 `lockState=LOCKED` 且 `opState=READY`
- 超时：可配置（例如5s~60s）
- 重试：可选“自动重锁”（模拟器阶段可不做；若做需记录次数并通过SSE可观测）
- 失败处理（方案B写死）：`LOCK_TIMEOUT`或`LOCK_LOST`→FAILED→SAFE→写`error.json`；并保证`/atmospheric_delay`可读到失败原因（`ApiResponse.data`为`error.json`内容）
- 输出：SSE `DEVICE_STATUS`、`LOG`

7) **MEASURE（执行三类比相测量）**
- 做什么：按`measurementPlan.modes`顺序执行，每个mode重复`repeat`次：
  - `startMeasurement(request)`→等待BUSY结束→`getMeasurementResult()`
- 模拟器应支持三类测量mode（验收用例A要求配方包含三种mode并能跑通；用例D用于验证“缺项失败”口径）：
  - `LINK`、`MAIN_INTERNAL`、`RELAY_INTERNAL`
- 超时：每次测量按`measurementTimeMs`估算；单次建议5s~60s
- 失败处理（方案B写死）：
  - `DEVICE_BUSY`：短暂重试（例如3次，每次200ms）
  - `RANDOM_LOST_LOCK`导致`lockState=LOST`：立刻FAILED→SAFE→写`error.json`；并保证`/atmospheric_delay`可读到失败原因（`ApiResponse.data`为`error.json`内容）
- 输出（方案B写死）：
  - SSE `MEASUREMENT_RESULT`（每条repeat一条）
  - 落盘：持续更新`measurement_result.json`
  - 无论成功/失败：`GET /api/runs/{runId}/measurement_result`必须可用（返回统一`ApiResponse`，其`data`为`measurement_result.json`内容；允许部分结果或空数组）

8) **SUMMARY（计算大气时延输出）**
- 计算公式（验收口径，写死）：
  - `atmosphericDelayNs = avg(LINK.delayNs) - avg(MAIN_INTERNAL.delayNs) - avg(RELAY_INTERNAL.delayNs)`
  - `uncertaintyNs = sqrt(stdLink^2 + stdMain^2 + stdRelay^2)`
- 有效性判定（验收口径，写死）：
  - 每个必要mode有效条数必须 ≥ `ceil(repeat * 0.7)`（排除`qualityFlag=INVALID`）
  - 若缺任一必要mode或有效条数不足：大气时延失败
- 失败处理（方案B写死）：
  - 大气时延失败：run必须 FAILED（或CANCELLED）；必须写`error.json`
  - 失败时是否生成`atmospheric_delay.json`不作为本轮验收硬要求（可选）
  - 无论是否生成`atmospheric_delay.json`：`GET /api/runs/{runId}/atmospheric_delay`必须始终可用并返回失败原因（写死：统一`ApiResponse`，`success=false`，`code`为具体失败错误码，`message`为中文原因，`data`为`error.json`内容）
- 输出：
  - 成功：建议推送SSE `ATMOSPHERIC_RESULT(status=SUCCEEDED)`，最终`DONE`
  - 失败：最终必须以SSE `FAILED`结束（最后一条）

9) **PERSIST（落盘保存）**
- 做什么：确保规定文件写入完成（见第8章方案B口径）
- 失败处理（方案B写死）：落盘失败同样FAILED→SAFE→必须写`error.json`（尽最大努力）

10) **DONE / FAILED / CANCELLED**
- DONE：写`run_info.json`结束态；推送SSE `DONE`并关闭流
- FAILED：执行SAFE；写`error.json`；推送SSE `FAILED`并关闭流
- CANCELLED：执行SAFE；写`error.json`；推送SSE `FAILED`或专用`CANCELLED`（若实现）并关闭流

---

## 5) REST API 列表（method + path + request/response示例）

> 路径风格统一：`/api/devices`、`/api/recipes`、`/api/runs`；链路模型参数通过Recipe传入。  
> 方案B硬要求：`/measurement_result`与`/atmospheric_delay`必须“始终可用”（成功/失败都能返回可解释内容）。

### 5.0 HTTP Status 与响应包裹（写死）

- **统一响应包裹**：除“下载/归档”这类二进制接口外，所有JSON接口都返回统一`ApiResponse`（第3.12节结构）。
- **HTTP Status 规则（减少上位机分支，且与常规语义兼容）**：
  - `200 OK`：业务成功；或业务失败但属于“可预期业务状态”（例如run本身FAILED导致查询大气时延返回error对象）。
  - `400 Bad Request`：请求参数校验失败（如缺少recipeId、枚举非法）。
  - `404 Not Found`：资源不存在（如runId不存在、recipeId不存在）。
  - `500 Internal Server Error`：服务端异常或不可恢复的落盘/解析错误。
  - 对于`400/404/500`：响应体仍应尽力返回`ApiResponse`（`success=false`，`code`为对应错误码，`message`为中文原因）。

### 5.1 设备
1) 查询两设备状态
- `GET /api/devices`

2) 查询单设备信息
- `GET /api/devices/{deviceId}/info`（`deviceId=MAIN|RELAY`）

3) 查询单设备状态
- `GET /api/devices/{deviceId}/status`

4) 手动进入SAFE
- `POST /api/devices/{deviceId}/safe`

### 5.2 配方（落盘到`./data/recipes/{recipeId}.json`）
1) 列表
- `GET /api/recipes`

2) 获取
- `GET /api/recipes/{recipeId}`

3) 创建/更新（幂等覆盖）
- `POST /api/recipes`

4) 删除
- `DELETE /api/recipes/{recipeId}`

### 5.3 运行（Run）
1) 启动run（选择recipe）
- `POST /api/runs`
- Request：
```json
{"recipeId":"RCP-001"}
```
- Response：
```json
{
  "success": true,
  "code": "OK",
  "message": "成功",
  "data": { "runId": "RUN-20260125-100001-001", "sseUrl": "/api/sse/runs/RUN-20260125-100001-001" },
  "ts": "..."
}
```

2) 查询run状态
- `GET /api/runs/{runId}`

3) 查询run测量结果（方案B硬要求：始终可用）
- `GET /api/runs/{runId}/measurement_result`
- 行为（写死）：
  - 返回统一`ApiResponse`，其`data`为`measurement_result.json`内容
  - `success=true`、`code=OK`、`message=成功`（查询接口本身成功；run是否失败由`/atmospheric_delay`统一提供失败原因）
  - 失败/取消run场景下：`data.results`允许为部分结果或`[]`，但文件与接口必须可读
  - HTTP Status（写死）：`runId`存在则必须返回`200 OK`；`runId`不存在则返回`404 Not Found`（并尽力返回`ApiResponse.success=false`）

4) 查询大气时延输出或失败原因（方案B硬要求：始终可用）
- `GET /api/runs/{runId}/atmospheric_delay`
- 行为（写死，减少上位机工作量）：
  - 成功：返回统一`ApiResponse`，其`data`为`atmospheric_delay.json`内容；并且`success=true`、`code=OK`、`message=成功`
  - 失败/取消：返回统一`ApiResponse`，其`data`为`error.json`内容（用于前端直接展示失败原因；无需额外解析AtmosphericDelayResult）；并且`success=false`、`code`建议直接使用具体失败错误码（如`LOCK_TIMEOUT`/`ATMOSPHERIC_FAILED`等）、`message`为中文原因
  - HTTP Status（写死）：`runId`存在则必须返回`200 OK`（即便`success=false`）；`runId`不存在则返回`404 Not Found`（并尽力返回`ApiResponse.success=false`）

5) 列出run目录文件
- `GET /api/runs/{runId}/files`

6) 下载run目录压缩包（可占位）
- `GET /api/runs/{runId}/archive`

7) 列出历史run（验收建议项）
- `GET /api/runs`

### 5.4 链路模型参数传递方式
- 通过Recipe字段`recipe.linkModel`传入；UI编辑Recipe即可修改链路参数

### 5.x 现有实现映射（REST端点对齐）

#### 5.x.1 现有项目REST端点清单（method + path）
设备：
- `GET /api/devices` ✅
- `GET /api/devices/{deviceId}/status` ✅
- `GET /api/devices/{deviceId}/info` ✅
- `POST /api/devices/{deviceId}/connection`（连接）✅（规范可视为connect动作资源）
- `DELETE /api/devices/{deviceId}/connection`（断开）✅
- `POST /api/devices/{deviceId}/safe` ✅

配方：
- `GET /api/recipes` ✅
- `GET /api/recipes/{recipeId}` ✅
- `POST /api/recipes` ✅
- `DELETE /api/recipes/{recipeId}` ✅

运行：
- `POST /api/runs` ✅
- `GET /api/runs/{runId}` ✅
- `GET /api/runs/{runId}/files` ✅
- `GET /api/runs/{runId}/archive` ✅
- `GET /api/runs/{runId}/measurement_result` ✅（符合“始终可用”要求）
- `GET /api/runs/{runId}/atmospheric_delay` ✅（成功读atmospheric_delay.json；失败读error.json，符合方案B写死行为）

#### 5.x.2 与规范不一致项与建议
- `GET /api/runs`（历史run列表）：现有实现缺失 ❌  
  - 建议策略：新增该端点（仅扫描`./data/runs/`目录），不影响上层编排/UI

---

## 6) SSE 事件格式（类型 + payload示例，中文前端要用）

### 6.1 SSE端点
- `GET /api/sse/runs/{runId}`

### 6.2 Envelope（统一格式，验收口径写死）
```json
{
  "type": "LOG",
  "runId": "RUN-...",
  "ts": "2026-01-25T10:00:01.100+08:00",
  "seq": 1,
  "payload": { }
}
```
规则（验收口径）：
- `seq`单调递增
- `DONE`或`FAILED`必须最后一条
- 服务端必须在发送`DONE`或`FAILED`后主动关闭SSE连接（客户端收到终止事件后也应关闭EventSource/连接）

### 6.3 事件类型与payload示例（验收口径）
建议最小事件类型集合：
- `LOG`（日志行）
- `STEP`（步骤推进）
- `DEVICE_STATUS`（主站/转发站状态更新）
- `MEASUREMENT_RESULT`（比相/时延测量结果）
- `ATMOSPHERIC_RESULT`（大气时延结果；成功时建议推送）
- `DONE`（运行成功结束）
- `FAILED`（运行失败/取消结束）

1) `LOG`
```json
{
  "type":"LOG","runId":"RUN-...","ts":"...","seq":12,
  "payload":{"level":"INFO","step":"WAIT_LOCKED","message":"等待两台设备进入LOCKED"}
}
```

2) `STEP`
```json
{
  "type":"STEP","runId":"RUN-...","ts":"...","seq":2,
  "payload":{"step":"APPLY_RECIPE","message":"下发配方并Apply"}
}
```

3) `DEVICE_STATUS`（payload为DeviceStatus）
```json
{
  "type":"DEVICE_STATUS","runId":"RUN-...","ts":"...","seq":3,
  "payload":{"deviceId":"MAIN","connected":true,"opState":"READY","lockState":"LOCKED","temperatureC":40.1,"alarms":[]}
}
```

4) `MEASUREMENT_RESULT`（payload为MeasurementResult）
```json
{
  "type":"MEASUREMENT_RESULT","runId":"RUN-...","ts":"...","seq":30,
  "payload":{"mode":"LINK","repeatIndex":0,"delayNs":801.23,"phaseDeg":16.02,"confidence":0.93,"qualityFlag":"OK"}
}
```

5) `ATMOSPHERIC_RESULT`（payload为AtmosphericDelayResult；成功时建议推送）
```json
{
  "type":"ATMOSPHERIC_RESULT","runId":"RUN-...","ts":"...","seq":80,
  "payload":{"formulaVersion":"atm-v1","status":"SUCCEEDED","atmosphericDelayNs":706.11,"uncertaintyNs":1.20}
}
```

6) `DONE`
```json
{"type":"DONE","runId":"RUN-...","ts":"...","seq":99,"payload":{"message":"运行完成"}}
```

7) `FAILED`
```json
{"type":"FAILED","runId":"RUN-...","ts":"...","seq":99,"payload":{"errorCode":"LOCK_TIMEOUT","message":"等待LOCKED超时"}}
```

### 6.4 前端展示建议（中文）
- 顶部：`runId`、`当前步骤`、`运行状态`
- 日志窗口：按`seq`滚动追加，支持复制
- 结果表格：每条repeat一行（mode/repeatIndex/delayNs/phaseDeg/confidence/qualityFlag）
- 大气时延卡片（方案B，减少上位机分支）：
  - 成功：显示`atmosphericDelayNs`与`uncertaintyNs`
  - 失败/取消：直接调用`GET /api/runs/{runId}/atmospheric_delay`读取失败原因（统一`ApiResponse`，其`data`为error对象，与`error.json`同结构）

### 6.x 现有实现映射（SSE对齐）
- SSE端点：`GET /api/sse/runs/{runId}` ✅
- Envelope字段：`type/runId/ts/seq/payload` ✅
- 事件类型：现有实现为 `LOG/STEP/DEVICE_STATUS/MEASUREMENT_RESULT/ATMOSPHERIC_RESULT/DONE/FAILED` ✅
- 规范建议的`RUN_STATUS`：现有实现未单独提供（但可用`STEP`覆盖）✅（以`STEP`为准，减少上位机工作量）

---

## 7) 中文页面/路由设计（最小可用）

### 7.1 页面1：设备状态
- 路由：`/ui/devices`
- 关键控件与中文文案：
  - `主站 (MAIN)` / `转发站 (RELAY)`
  - 状态徽章：`opState/lockState`
  - 字段：`连接状态`、`温度(°C)`、`告警`、`版本`
  - 按钮：`连接`、`断开`、`进入SAFE`

### 7.2 页面2：配方管理
- 路由：`/ui/recipes`
- 关键控件与中文文案：
  - 左侧：`配方列表`下拉 + `加载` / `新建` / `删除`
  - 右侧：`配方JSON`编辑框 + `保存/覆盖` / `格式化`
  - 提示：`提示：此页采用JSON编辑方式，字段名严格对齐后端领域模型`

### 7.3 页面3：一键测量
- 路由：`/ui/run`
- 关键控件与中文文案：
  - `选择配方`下拉 + `开始`
  - `实时日志（SSE）`
  - `测量结果（每次repeat一条）`
  - `大气时延输出`
  - `run目录文件` + `下载run.zip`
- 失败展示策略（方案B，减少上位机工作量）：
  - SSE收到`FAILED`后，直接请求`GET /api/runs/{runId}/atmospheric_delay`并将返回内容（统一`ApiResponse.data`）作为失败原因卡片展示

---

## 8) 落盘目录与文件规范（必须写死）

根目录：`./data`  
run目录：`./data/runs/{runId}/`  
recipe目录：`./data/recipes/{recipeId}.json`

### 8.1 Run目录必需/可选文件（方案B验收口径，写死）

#### 8.1.1 所有run（成功/失败/取消）都必须存在的必需文件
- `recipe.json`（必需）
- `device_info.json`（必需）
- `run_info.json`（必需）
- `logs.ndjson`（必需）
- `measurement_result.json`（必需；允许 `results=[]` 但文件必须存在）

#### 8.1.2 成功run（SUCCEEDED）额外必需文件
- `atmospheric_delay.json`（必需；`status=SUCCEEDED`，包含`atmosphericDelayNs/uncertaintyNs/inputsSnapshot`）

#### 8.1.3 失败/取消run（FAILED/CANCELLED）额外必需文件
- `error.json`（必需）

#### 8.1.4 失败/取消run的可选文件（不作为本轮验收硬要求）
- `atmospheric_delay.json`（可选）
  - 若生成：必须 `status=FAILED` 且包含 `error`
  - 可选加分项：若实现方支持，建议由orchestrator在进入`FAILED/CANCELLED`前尽力生成；缺失不影响本轮验收

#### 8.1.5 可选文件（用于调试/回放）
- `status_snapshot.json`（可选文件）
  - 仅用于调试/回放；不得替代`run_info.json`

### 8.2 每个文件内容结构（验收口径）
1) `recipe.json`
- 内容：本次run使用的Recipe完整快照

2) `device_info.json`
- 内容：两台设备的DeviceInfo快照 + 生成时间

3) `run_info.json`
- 内容：run元信息与状态（至少包含runId、recipeId、startedAt、endedAt、status/state、step、error摘要）

4) `logs.ndjson`
- 内容：每行一个JSON日志对象（便于流式写入与后处理）
- 行结构示例：
```json
{"ts":"...","runId":"RUN-...","level":"INFO","step":"APPLY_RECIPE","message":"主站配置下发并开始生效"}
```

5) `measurement_result.json`
- 内容：测量结果集合（results数组，每次repeat一条）
- 顶层结构（写死，必需字段）：
  - `runId`: string
  - `recipeId`: string
  - `results`: MeasurementResult[]
- 必须包含字段：`mode/repeatIndex/delayNs/phaseDeg/confidence/qualityFlag`
- 推荐排序（写死，用于验收一致性）：按`measurementPlan.modes`顺序分组，每个mode内按`repeatIndex`升序
- 示例（允许`results=[]`）：
  ```json
  {
    "runId": "RUN-20260125-100001-001",
    "recipeId": "RCP-DEFAULT",
    "results": []
  }
  ```
- 失败/取消run允许：
  - `results`为部分结果或空数组，但文件必须存在且可被API读取

6) `atmospheric_delay.json`
- 成功run（必需）：
  - 必须存在
  - `status=SUCCEEDED`
  - 必须包含：`atmosphericDelayNs/uncertaintyNs/inputsSnapshot`
- 失败/取消run（可选，不作为本轮验收硬要求）：
  - 若存在：必须 `status=FAILED` 且包含 `error`（数值字段可为null）
  - 可选加分项：建议由orchestrator在进入`FAILED/CANCELLED`前尽力生成；缺失不影响本轮验收

7) `error.json`（FAILED/CANCELLED必需）
- 内容：失败/取消原因（errorCode + 中文message + step + ts）
- 用于支撑方案B API行为：`GET /api/runs/{runId}/atmospheric_delay`在失败/取消时直接返回该内容
- 字段结构（写死，示例）：
  ```json
  {
    "ts": "2026-01-25T10:00:08.000+08:00",
    "step": "WAIT_LOCKED",
    "errorCode": "LOCK_TIMEOUT",
    "message": "等待LOCKED超时"
  }
  ```
 - 字段名写死：实现方允许附加字段，但不得更改上述字段名及其语义

8) `status_snapshot.json`（可选）
- 内容：关键时刻状态快照（用于调试/回放）

### 8.3 现有实现映射（落盘对齐，按方案B结论）
现有项目run目录实际落盘文件：
- `recipe.json` ✅
- `device_info.json` ✅
- `run_info.json` ✅
- `logs.ndjson` ✅
- `measurement_result.json` ✅（失败时也存在；可为空数组）
- `error.json`
  - FAILED：生成 ✅（符合方案B：失败/取消额外必需）
  - CANCELLED：现阶段未实现 ❌（建议预留）
- `atmospheric_delay.json`
  - 成功run：生成 ✅（符合方案B：成功额外必需）
  - 失败run：不生成或不保证 ✅（符合方案B：失败时可选，不纳入本轮硬要求）
- `status_snapshot.json`：未生成 ✅（本规范定义为可选）

---

## 附：模拟器可复现规则（最终验收口径，写死）

- `seedKey = runId + "|" + recipeId + "|" + mode + "|" + repeatIndex`
- `seed = SHA-256(seedKey)`的前8字节按**大端（Big-Endian）**解析为**signed long**
- 所有模拟测量输出（`delayNs/phaseDeg/confidence/qualityFlag`，以及可选`snrDb`等）必须仅由上述seedKey与当前配置决定，确保“可解释、可重复”

示例（固定示例，用于验收对齐）：
- `seedKey`：
  - `RUN-20260125-100001-001|RCP-001|LINK|0`
- `SHA-256(seedKey)`前8字节（hex）：
  - `1f60c41052dc0ff0`
- `seedBigEndianSignedLong`：
  - `2261022587328663536`

---

## 附录：验收用例（可执行，方案B口径）

> 说明：以下用例以“方案B验收口径”为准：失败/取消不再强制检查`atmospheric_delay.json`存在，但必须验证`/atmospheric_delay` API始终可用并能返回失败原因（写死：统一`ApiResponse`，其`data`为`error.json`内容）。  
> 加分项：失败/取消若存在`atmospheric_delay.json`，可额外校验其`status=FAILED`与`error`结构。

### 用例A：成功 run（SUCCEEDED）
前置条件：
- 服务已启动（端口8080），默认故障注入关闭：`faultType=NONE`
- 存在一个完整配方，且measurementPlan包含：`LINK/MAIN_INTERNAL/RELAY_INTERNAL`，repeat≥1

操作步骤：
1) 启动run：
```bash
curl -X POST http://localhost:8080/api/runs -H "Content-Type: application/json" -d "{\"recipeId\":\"RCP-DEFAULT\"}"
```
2) 浏览器打开：`http://localhost:8080/ui/run`，选择同一配方点击`开始`
3) 或用curl订阅SSE：
```bash
curl -N http://localhost:8080/api/sse/runs/{runId}
```
4) 检查落盘目录：`./data/runs/{runId}/`
5) 验证API：
```bash
curl http://localhost:8080/api/runs/{runId}/measurement_result
curl http://localhost:8080/api/runs/{runId}/atmospheric_delay
```

预期SSE事件序列（类型顺序，允许中间穿插多条LOG/DEVICE_STATUS/MEASUREMENT_RESULT）：
- `STEP(INIT)`
- `STEP(CHECK_DEVICES)`
- `STEP(APPLY_RECIPE)`
- `STEP(LOCK_START)`
- `STEP(WAIT_LOCKED)`
- `STEP(MEASURE)` + 多条`MEASUREMENT_RESULT`
- `STEP(SUMMARY)` + `ATMOSPHERIC_RESULT(status=SUCCEEDED)`
- `DONE`（最后一条）

预期落盘文件集合（方案B）：
- 必有（所有run必需）：`recipe.json`、`device_info.json`、`run_info.json`、`logs.ndjson`、`measurement_result.json`
- 成功额外必需：`atmospheric_delay.json`
- 必无：`error.json`
- 可选：`status_snapshot.json`

预期API行为（方案B）：
- `GET /api/runs/{runId}/measurement_result`：返回统一`ApiResponse`，`success=true`、`code=OK`、`message=成功`，其`data`为`measurement_result.json`内容（results可为空）
- `GET /api/runs/{runId}/atmospheric_delay`：返回统一`ApiResponse`，`success=true`、`code=OK`、`message=成功`，其`data`为`atmospheric_delay.json`内容（SUCCEEDED）

### 用例B：LOCK_TIMEOUT 失败（FAILED）
前置条件：
- 开启故障注入：`faultType=LOCK_TIMEOUT`（通过配置文件或配方simulatorProfile）

操作步骤：
1) 启动run（同用例A）
2) 订阅SSE并观察最终事件
3) 检查落盘目录
4) 验证API（必须）：
```bash
curl http://localhost:8080/api/runs/{runId}/measurement_result
curl http://localhost:8080/api/runs/{runId}/atmospheric_delay
```
5) （加分项）若`atmospheric_delay.json`存在，则校验其`status=FAILED`与`error`结构

预期SSE事件序列：
- 前置步骤同A直到`STEP(WAIT_LOCKED)`
- 最终必须：`FAILED`（最后一条，payload.errorCode=LOCK_TIMEOUT）

预期落盘文件集合（方案B）：
- 必有（所有run必需）：`recipe.json`、`device_info.json`、`run_info.json`、`logs.ndjson`、`measurement_result.json`（可为空数组）
- 失败额外必需：`error.json`
- `atmospheric_delay.json`：不要求存在（可选）

预期API行为（方案B写死）：
- `GET /api/runs/{runId}/measurement_result`：必须返回统一`ApiResponse`，`success=true`、`code=OK`、`message=成功`，其`data`为`measurement_result.json`内容（可能空数组或部分结果）
- `GET /api/runs/{runId}/atmospheric_delay`：必须返回统一`ApiResponse`，`success=false`、`code`为具体失败错误码、`message`为中文原因，其`data`为`error.json`内容（包含errorCode/message）

### 用例C：RANDOM_LOST_LOCK 失败（FAILED）
前置条件：
- 开启故障注入：`faultType=RANDOM_LOST_LOCK`
- 为便于验收，建议将失锁概率设置为1.0（保证必现）

操作步骤：
1) 启动run
2) 订阅SSE：可看到`DEVICE_STATUS.lockState=LOST`（建议可观测）
3) 最终必须收到`FAILED`
4) 检查落盘目录
5) 验证API（必须）：
```bash
curl http://localhost:8080/api/runs/{runId}/measurement_result
curl http://localhost:8080/api/runs/{runId}/atmospheric_delay
```
6) （加分项）若`atmospheric_delay.json`存在，则校验其`status=FAILED`与`error`结构

预期SSE事件序列：
- 进入`STEP(MEASURE)`后或测量前发生失锁
- 最终必须：`FAILED`（最后一条，payload.errorCode=LOCK_LOST或等价错误码）

预期落盘文件集合（方案B）：
- 必有（所有run必需）：`recipe.json`、`device_info.json`、`run_info.json`、`logs.ndjson`、`measurement_result.json`（可能部分结果）
- 失败额外必需：`error.json`
- `atmospheric_delay.json`：不要求存在（可选）

预期API行为（方案B写死）：
- `GET /api/runs/{runId}/measurement_result`：必须返回统一`ApiResponse`，`success=true`、`code=OK`、`message=成功`，其`data`为`measurement_result.json`内容（部分结果或空）
- `GET /api/runs/{runId}/atmospheric_delay`：必须返回统一`ApiResponse`，`success=false`、`code`为具体失败错误码、`message`为中文原因，其`data`为`error.json`内容

### 用例D：大气时延缺项导致失败（FAILED）
最终口径（方案B写死）：
- 若`measurementPlan`缺少`MAIN_INTERNAL`或`RELAY_INTERNAL`或`LINK`，则大气时延无法计算，run必须FAILED，并生成`error.json`；失败时不强制生成`atmospheric_delay.json`，但`/atmospheric_delay` API必须返回失败原因（error.json）。

前置条件：
- 创建一个配方：`measurementPlan.modes=["LINK","RELAY_INTERNAL"]`（故意缺`MAIN_INTERNAL`），repeat≥1

操作步骤：
1) 保存该配方（`POST /api/recipes`）
2) 以该配方启动run（`POST /api/runs`）
3) 订阅SSE并观察最终事件
4) 检查落盘目录
5) 验证API（必须）：
```bash
curl http://localhost:8080/api/runs/{runId}/measurement_result
curl http://localhost:8080/api/runs/{runId}/atmospheric_delay
```
6) （加分项）若`atmospheric_delay.json`存在，则校验其`status=FAILED`与`error`结构

预期SSE事件序列：
- 可进入`MEASURE`并产生部分`MEASUREMENT_RESULT`
- `SUMMARY`阶段判定缺项
- 最终必须：`FAILED`（最后一条，payload.errorCode=ATMOSPHERIC_FAILED或等价）

预期落盘文件集合（方案B）：
- 必有（所有run必需）：`recipe.json`、`device_info.json`、`run_info.json`、`logs.ndjson`、`measurement_result.json`
- 失败额外必需：`error.json`
- `atmospheric_delay.json`：不要求存在（可选）

预期API行为（方案B写死）：
- `GET /api/runs/{runId}/measurement_result`：必须返回统一`ApiResponse`，`success=true`、`code=OK`、`message=成功`，其`data`为`measurement_result.json`内容（部分结果或空）
- `GET /api/runs/{runId}/atmospheric_delay`：必须返回统一`ApiResponse`，`success=false`、`code`为具体失败错误码、`message`为中文原因，其`data`为`error.json`内容（包含“缺少测量项: MAIN_INTERNAL”等中文原因）
