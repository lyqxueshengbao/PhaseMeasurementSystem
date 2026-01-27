package com.example.pj125.run;

import com.example.pj125.common.ApiException;
import com.example.pj125.common.AppProperties;
import com.example.pj125.common.ErrorCode;
import com.example.pj125.common.JsonUtils;
import com.example.pj125.device.DeviceInfo;
import com.example.pj125.measurement.MeasurementResultFile;
import com.example.pj125.recipe.Recipe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class RunPersistenceService {
    private final ObjectMapper mapper = JsonUtils.mapper();
    private final Path runsDir;

    public RunPersistenceService(AppProperties props) {
        this.runsDir = Path.of(props.getDataDir(), "runs");
    }

    public Path runDir(String runId) {
        return runsDir.resolve(runId);
    }

    public void initRun(String runId, Recipe recipe, Map<String, Object> deviceInfo, RunInfo runInfo) {
        try {
            Files.createDirectories(runDir(runId));
            writeJson(runId, "recipe.json", recipe);
            writeJson(runId, "device_info.json", deviceInfo);
            writeJson(runId, "run_info.json", runInfo);

            Path logs = runDir(runId).resolve("logs.ndjson");
            if (!Files.exists(logs)) Files.writeString(logs, "");

            MeasurementResultFile mrf = new MeasurementResultFile();
            mrf.setRunId(runId);
            mrf.setRecipeId(recipe.getRecipeId());
            writeJson(runId, "measurement_result.json", mrf);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "初始化run目录失败: " + e.getMessage());
        }
    }

    public void writeRunInfo(String runId, RunInfo runInfo) {
        writeJson(runId, "run_info.json", runInfo);
    }

    public void appendLog(String runId, RunLogLine line) {
        Path p = runDir(runId).resolve("logs.ndjson");
        try {
            Files.createDirectories(p.getParent());
            String json = mapper.writeValueAsString(line);
            Files.writeString(p, json + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "写入logs.ndjson失败: " + e.getMessage());
        }
    }

    public void writeMeasurementResult(String runId, MeasurementResultFile file) {
        writeJson(runId, "measurement_result.json", file);
    }

    public void writeAtmosphericResult(String runId, AtmosphericDelayResult file) {
        writeJson(runId, "atmospheric_delay.json", file);
    }

    public void writeError(String runId, RunError error) {
        writeJson(runId, "error.json", error);
    }

    public Map<String, Long> listFiles(String runId) {
        Path dir = runDir(runId);
        if (!Files.exists(dir)) throw new ApiException(ErrorCode.NOT_FOUND, "run不存在: " + runId);
        try (var stream = Files.list(dir)) {
            Map<String, Long> out = new LinkedHashMap<>();
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            out.put(p.getFileName().toString(), Files.size(p));
                        } catch (IOException e) {
                            out.put(p.getFileName().toString(), -1L);
                        }
                    });
            return out;
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "读取run文件列表失败: " + e.getMessage());
        }
    }

    public List<RunInfo> listRuns() {
        if (!Files.exists(runsDir)) return List.of();
        try (var stream = Files.list(runsDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .map(runId -> {
                        try {
                            return readJson(runId, "run_info.json", RunInfo.class);
                        } catch (Exception e) {
                            RunInfo ri = new RunInfo();
                            ri.setRunId(runId);
                            return ri;
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "读取run列表失败: " + e.getMessage());
        }
    }

    public <T> T readJson(String runId, String fileName, Class<T> clazz) {
        Path p = runDir(runId).resolve(fileName);
        if (!Files.exists(p)) throw new ApiException(ErrorCode.NOT_FOUND, "文件不存在: " + fileName);
        try {
            return mapper.readValue(Files.readString(p), clazz);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "读取文件失败: " + fileName + ", " + e.getMessage());
        }
    }

    public ResponseEntity<InputStreamResource> archiveZip(String runId) {
        Path dir = runDir(runId);
        if (!Files.exists(dir)) throw new ApiException(ErrorCode.NOT_FOUND, "run不存在: " + runId);
        try {
            Path tmp = Files.createTempFile("pj125-" + runId + "-", ".zip");
            tmp.toFile().deleteOnExit();
            try (OutputStream os = Files.newOutputStream(tmp);
                 ZipOutputStream zos = new ZipOutputStream(os)) {
                try (var stream = Files.list(dir)) {
                    for (Path p : stream.toList()) {
                        if (!Files.isRegularFile(p)) continue;
                        ZipEntry entry = new ZipEntry(p.getFileName().toString());
                        zos.putNextEntry(entry);
                        Files.copy(p, zos);
                        zos.closeEntry();
                    }
                }
            }
            InputStream is = Files.newInputStream(tmp);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + runId + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(is));
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "打包zip失败: " + e.getMessage());
        }
    }

    private void writeJson(String runId, String fileName, Object obj) {
        Path p = runDir(runId).resolve(fileName);
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PERSIST_FAILED, "写入文件失败: " + fileName + ", " + e.getMessage());
        }
    }

    public static Map<String, Object> buildDeviceInfoPayload(Map<com.example.pj125.device.DeviceId, DeviceInfo> infos) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", OffsetDateTime.now().toString());
        List<Map<String, Object>> list = new ArrayList<>();
        for (var e : infos.entrySet()) {
            DeviceInfo i = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceId", i.getDeviceId().name());
            m.put("model", i.getModel());
            // spec fields
            m.put("serialNumber", i.getSerialNumber());
            m.put("firmwareVersion", i.getFirmwareVersion());
            m.put("protocolVersion", i.getProtocolVersion());
            // backward compatible fields (keep old names)
            m.put("serial", i.getSerial());
            m.put("version", i.getVersion());
            list.add(m);
        }
        out.put("devices", list);
        return out;
    }
}
