# pj125 比相/时延测量上位机（后端 + Web UI）

本项目为“比相/时延测量上位机系统”的后端服务 + 浏览器界面（中文），当前阶段使用纯软件模拟器（SimulatedMainStation/SimulatedRelayStation），以便固化流程与接口作为外包验收指标；未来真机到位后，尽量仅替换 `device` 实现层（例如 `TcpMainStation/TcpRelayStation`），上层 orchestrator/UI 保持不改。

## 启动

要求：JDK 17+，Maven 3.8+

```bash
mvn spring-boot:run
```

默认端口：`8080`  
数据目录：`./data`

## 页面入口（中文）

- 设备状态页：`http://localhost:8080/ui/devices`
- 配方管理页：`http://localhost:8080/ui/recipes`
- 一键测量页：`http://localhost:8080/ui/run`

## 关键约定（必须口径）

- 设备状态：`opState` + `lockState` 两维（不混用单一 state）
- 测量结果每条 repeat 必有字段：
  - `ts` (ISO-8601)
  - `delayNs` (ns)
  - `phaseDeg` (deg, 约束到 [-180,180])
  - `confidence` (0~1)
  - `qualityFlag` (OK|WARN|BAD|INVALID)
- 测量计划：
  - `measurementPlan.modes`：按顺序执行
  - `measurementPlan.repeat`：每个 mode 重复 repeat 次，每次生成一条结果（带 `repeatIndex`）
- 可复现模拟测量：
  - `seedKey = runId + "|" + recipeId + "|" + mode + "|" + repeatIndex`
  - `seed = SHA-256(seedKey)` 前 8 字节转 `long`（Java 标准库）
- SSE 单端点：`GET /api/sse/runs/{runId}`
  - 事件类型：`LOG/STEP/DEVICE_STATUS/MEASUREMENT_RESULT/ATMOSPHERIC_RESULT/DONE/FAILED`
  - 统一 envelope：
    ```json
    {
      "type": "LOG",
      "runId": "RUN-...",
      "ts": "ISO-8601",
      "seq": 1,
      "payload": { }
    }
    ```
- Run 落盘（每次必须生成）：
  - `./data/runs/{runId}/recipe.json`
  - `./data/runs/{runId}/device_info.json`
  - `./data/runs/{runId}/run_info.json`
  - `./data/runs/{runId}/logs.ndjson`
  - `./data/runs/{runId}/measurement_result.json`
  - 成功：`./data/runs/{runId}/atmospheric_delay.json`（`status=SUCCEEDED`，含`atmosphericDelayNs/uncertaintyNs/inputsSnapshot`）
  - 失败：`./data/runs/{runId}/error.json`
- Recipe 落盘：`./data/recipes/{recipeId}.json`
- 方案B硬要求（验收写死）：
  - `GET /api/runs/{runId}/measurement_result`：run存在即 `200`，且始终返回 `success=true, code=OK, message=成功`；`data` 为 `measurement_result.json` 内容（可部分/空）
  - `GET /api/runs/{runId}/atmospheric_delay`：run存在即 `200`
    - 成功：返回 `success=true, code=OK, message=成功`；`data` 为 `atmospheric_delay.json`
    - 失败：返回 `success=false`，`code` 为具体失败错误码（如 `LOCK_TIMEOUT/ATMOSPHERIC_FAILED/...`），`message` 为中文原因；`data` 为 `error.json`

## 架构边界（验收自检）

为保证“真机替换/两机部署”时不推翻上层流程，约定依赖边界如下：
- **允许出现 `SimulatedStation` 的层**：`com.example.pj125.device`（本地模拟器与 `DeviceGateway` 实现），以及未来的 infra/remote device 实现
- **禁止出现 `SimulatedStation` 的层**：`orchestrator`、`run/service`、`api/web`、`ui`（这些层只能依赖 `Device` / `DeviceGateway` 等抽象）

静态自检（ripgrep）当前命中（应只在 device/gateway 层）：
- `import com.example.pj125.device.SimulatedStation`：无命中
- `instanceof SimulatedStation`：`src/main/java/com/example/pj125/device/DeviceManager.java`
- `getAppliedConfig(`：仅 `src/main/java/com/example/pj125/device/SimulatedStation.java`
- `/api/runs/{runId}/measurement_result`：`src/main/java/com/example/pj125/api/RunsController.java` 返回 `Ack.ok(readJson(...measurement_result.json...))`（始终 `success=true, code=OK`）
- `/api/runs/{runId}/atmospheric_delay`：`src/main/java/com/example/pj125/api/RunsController.java`
  - 成功：存在 `atmospheric_delay.json` 则 `Ack.ok(...)`
  - 失败：存在 `error.json` 则 `Ack.fail(code, err.message)` 且 `data=err`（HTTP 200，run存在时）

## MAIN/RELAY endpoint 预留（未来两机拆分）

当前默认仍是单体本地模拟器（满足本轮 SPEC.md / 方案B 验收口径），但提前把主站/转发站的“连接端点”做成可配置，便于未来拆到两台机器（相距2km）：

- 配置项（默认不变）见 `src/main/resources/application.yml`：
  - `pj125.devices.main.endpoint: local-sim`
  - `pj125.devices.relay.endpoint: local-sim`
- `local-sim`：使用进程内模拟器（当前验收口径）
- `host:port`：预留给未来的远程设备 Agent/HTTP 实现
  - **注意：当前版本远程实现仅占位**，配置为 `host:port` 会走占位实现并导致设备呈现为**离线/不可连接（例如返回 `DEVICE_OFFLINE`）**，用于尽早暴露配置错误；不影响本轮验收流程

## DDS（仅主站 MAIN）配置说明

新增 `DeviceConfig.ddsFreqHz`（单位 Hz）作为 DDS 输出频率控制字：
- 仅对主站 `MAIN` 生效；`RELAY` 即使在 `relayConfig.ddsFreqHz` 中填写也会被忽略，但不会报错
- 生效证据（验收可回读）：每次 run 的 `APPLY_RECIPE` 完成后，会把两台设备的 `readbackConfig()` 快照写入 `./data/runs/{runId}/run_info.json`：
  - `mainAppliedConfig.ddsFreqHz`
  - `relayAppliedConfig.ddsFreqHz`

最小演示（curl）：
```bash
curl -X POST http://localhost:8080/api/recipes ^
  -H "Content-Type: application/json" ^
  -d "{\"recipeId\":\"RCP-DDS-DEMO\",\"name\":\"DDS demo (MAIN only)\",\"mainConfig\":{\"txEnable\":true,\"ddsFreqHz\":10000000.0,\"referencePathDelayNs\":120.0,\"measurePathDelayNs\":180.0},\"relayConfig\":{\"txEnable\":true,\"ddsFreqHz\":123.0,\"referencePathDelayNs\":95.0,\"measurePathDelayNs\":130.0},\"linkModel\":{\"fixedLinkDelayNs\":800.0,\"driftPpm\":0.2,\"noiseStdNs\":0.5,\"basePhaseDeg\":15.0},\"measurementPlan\":{\"modes\":[\"LINK\",\"MAIN_INTERNAL\",\"RELAY_INTERNAL\"],\"repeat\":2},\"simulatorProfile\":null}"

curl -X POST http://localhost:8080/api/runs ^
  -H "Content-Type: application/json" ^
  -d "{\"recipeId\":\"RCP-DDS-DEMO\"}"

curl http://localhost:8080/api/runs/{runId}
```
`GET /api/runs/{runId}` 的 `data.mainAppliedConfig.ddsFreqHz` 应为 `10000000.0`，而 `data.relayAppliedConfig.ddsFreqHz` 应为 `null`（RELAY 忽略）。


## 示例 Recipe（JSON）

可在配方管理页新建/编辑/保存；也可用 curl 直接保存：

```json
{
  "recipeId": "RCP-001",
  "name": "默认联调配方",
  "mainConfig": { "txEnable": true, "referencePathDelayNs": 120.0, "measurePathDelayNs": 180.0 },
  "relayConfig": { "txEnable": true, "referencePathDelayNs": 95.0, "measurePathDelayNs": 130.0 },
  "linkModel": { "fixedLinkDelayNs": 800.0, "driftPpm": 0.2, "noiseStdNs": 0.5, "basePhaseDeg": 15.0 },
  "measurementPlan": { "modes": ["LINK","MAIN_INTERNAL","RELAY_INTERNAL"], "repeat": 8 },
  "simulatorProfile": {
    "applyDelayMs": 300,
    "lockTimeMs": 1200,
    "measurementTimeMs": 800,
    "faultType": "NONE",
    "randomLostLockProbability": 0.5
  }
}
```

保存：
```bash
curl -X POST http://localhost:8080/api/recipes ^
  -H "Content-Type: application/json" ^
  --data-binary "@recipe.json"
```

## 用 curl 触发一次 run（最小可验证流程）

```bash
curl -X POST http://localhost:8080/api/runs ^
  -H "Content-Type: application/json" ^
  -d "{\"recipeId\":\"RCP-DEFAULT\"}"
```

返回示例：
```json
{"success":true,"code":"OK","message":"成功","data":{"runId":"RUN-...","sseUrl":"/api/sse/runs/RUN-..."},"ts":"..."}
```

订阅 SSE：
```bash
curl -N http://localhost:8080/api/sse/runs/RUN-...
```

查看 run 落盘文件列表：
```bash
curl http://localhost:8080/api/runs/RUN-.../files
```

列出历史run：
```bash
curl http://localhost:8080/api/runs
```

下载 zip：
```bash
curl -L -o RUN.zip http://localhost:8080/api/runs/RUN-.../archive
```

## 一键验收自测脚本（可选）

在 Windows PowerShell 下可直接跑 A/B/C/D 四个用例的最小验收检查（SSE/落盘/API），不会修改任何对外接口口径：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-abcd.ps1 -StartServer
```

## 可复现性验证（同一 runId 重放一致）

本实现保证：同一 `runId + recipeId + mode + repeatIndex` 的模拟输出严格一致。

建议验证方式：
1. 触发 run，记录 `runId`
2. 查看 `./data/runs/{runId}/measurement_result.json`
3. 保留该文件不动，刷新 `/ui/run` 页面重新订阅 SSE（SseHub 会回放历史事件），表格内容应与落盘一致

## 用例A/B/C/D（按 SPEC.md 方案B口径，可直接验收）

提示：以下示例均不需要改 `application.yml`，通过“新建配方 + simulatorProfile”实现故障注入。

### 用例A：成功 run（SUCCEEDED）
1) 启动run（默认配方启动时自动落盘）：
```bash
curl -X POST http://localhost:8080/api/runs -H "Content-Type: application/json" -d "{\"recipeId\":\"RCP-DEFAULT\"}"
```
2) 订阅SSE：
```bash
curl -N http://localhost:8080/api/sse/runs/{runId}
```
3) 落盘检查点：`./data/runs/{runId}/` 必有 `recipe.json/device_info.json/run_info.json/logs.ndjson/measurement_result.json`，且成功额外必有 `atmospheric_delay.json`，必无 `error.json`
4) API检查点：
```bash
curl http://localhost:8080/api/runs/{runId}/measurement_result
curl http://localhost:8080/api/runs/{runId}/atmospheric_delay
```

### 用例B：LOCK_TIMEOUT 失败（FAILED）
1) 写入配方（示例ID：`RCP-LOCK-TIMEOUT`）：
```bash
curl -X POST http://localhost:8080/api/recipes -H "Content-Type: application/json" -d "{\"recipeId\":\"RCP-LOCK-TIMEOUT\",\"name\":\"锁定超时失败\",\"mainConfig\":{\"txEnable\":true,\"referencePathDelayNs\":120.0,\"measurePathDelayNs\":180.0},\"relayConfig\":{\"txEnable\":true,\"referencePathDelayNs\":95.0,\"measurePathDelayNs\":130.0},\"linkModel\":{\"fixedLinkDelayNs\":800.0,\"driftPpm\":0.2,\"noiseStdNs\":0.5,\"basePhaseDeg\":15.0},\"measurementPlan\":{\"modes\":[\"LINK\",\"MAIN_INTERNAL\",\"RELAY_INTERNAL\"],\"repeat\":8},\"simulatorProfile\":{\"faultType\":\"LOCK_TIMEOUT\"}}"
```
2) 启动run：
```bash
curl -X POST http://localhost:8080/api/runs -H "Content-Type: application/json" -d "{\"recipeId\":\"RCP-LOCK-TIMEOUT\"}"
```
3) 订阅SSE并确认最后一条为 `FAILED`（payload.errorCode=LOCK_TIMEOUT）
4) 落盘检查点：必有 `measurement_result.json`（允许空/部分），失败额外必有 `error.json`，不要求存在 `atmospheric_delay.json`
5) API检查点（必须）：
```bash
curl http://localhost:8080/api/runs/{runId}/measurement_result
curl http://localhost:8080/api/runs/{runId}/atmospheric_delay
```

### 用例C：RANDOM_LOST_LOCK 失败（FAILED）
1) 写入配方（示例ID：`RCP-LOST-LOCK`，概率设为1.0必现）：
```bash
curl -X POST http://localhost:8080/api/recipes -H "Content-Type: application/json" -d "{\"recipeId\":\"RCP-LOST-LOCK\",\"name\":\"随机失锁失败\",\"mainConfig\":{\"txEnable\":true,\"referencePathDelayNs\":120.0,\"measurePathDelayNs\":180.0},\"relayConfig\":{\"txEnable\":true,\"referencePathDelayNs\":95.0,\"measurePathDelayNs\":130.0},\"linkModel\":{\"fixedLinkDelayNs\":800.0,\"driftPpm\":0.2,\"noiseStdNs\":0.5,\"basePhaseDeg\":15.0},\"measurementPlan\":{\"modes\":[\"LINK\",\"MAIN_INTERNAL\",\"RELAY_INTERNAL\"],\"repeat\":8},\"simulatorProfile\":{\"faultType\":\"RANDOM_LOST_LOCK\",\"randomLostLockProbability\":1.0}}"
```
2) 启动run + 订阅SSE，观察 `DEVICE_STATUS.lockState=LOST`（可观测）后最终 `FAILED`（payload.errorCode=LOCK_LOST）
3) API检查点（必须）：
```bash
curl http://localhost:8080/api/runs/{runId}/measurement_result
curl http://localhost:8080/api/runs/{runId}/atmospheric_delay
```

### 用例D：大气时延缺项导致失败（FAILED）
1) 写入配方（示例ID：`RCP-MISSING-MAIN`，故意缺 `MAIN_INTERNAL`）：
```bash
curl -X POST http://localhost:8080/api/recipes -H "Content-Type: application/json" -d "{\"recipeId\":\"RCP-MISSING-MAIN\",\"name\":\"缺MAIN_INTERNAL导致大气时延失败\",\"mainConfig\":{\"txEnable\":true,\"referencePathDelayNs\":120.0,\"measurePathDelayNs\":180.0},\"relayConfig\":{\"txEnable\":true,\"referencePathDelayNs\":95.0,\"measurePathDelayNs\":130.0},\"linkModel\":{\"fixedLinkDelayNs\":800.0,\"driftPpm\":0.2,\"noiseStdNs\":0.5,\"basePhaseDeg\":15.0},\"measurementPlan\":{\"modes\":[\"LINK\",\"RELAY_INTERNAL\"],\"repeat\":8},\"simulatorProfile\":null}"
```
2) 启动run + 订阅SSE，确认 `SUMMARY` 后最终 `FAILED`（payload.errorCode=ATMOSPHERIC_FAILED 或等价）
3) API检查点（必须）：`/measurement_result` 必须成功可读，`/atmospheric_delay` 必须返回失败原因（error.json）
```bash
curl http://localhost:8080/api/runs/{runId}/measurement_result
curl http://localhost:8080/api/runs/{runId}/atmospheric_delay
```
