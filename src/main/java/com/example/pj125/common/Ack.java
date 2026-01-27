package com.example.pj125.common;

import java.time.OffsetDateTime;

public class Ack<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private String ts;

    public static <T> Ack<T> ok(T data) {
        Ack<T> ack = new Ack<>();
        ack.success = true;
        ack.code = ErrorCode.OK.name();
        ack.message = "成功";
        ack.data = data;
        ack.ts = OffsetDateTime.now().toString();
        return ack;
    }

    public static <T> Ack<T> fail(ErrorCode code, String message) {
        Ack<T> ack = new Ack<>();
        ack.success = false;
        ack.code = code.name();
        ack.message = message;
        ack.ts = OffsetDateTime.now().toString();
        return ack;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }
}

