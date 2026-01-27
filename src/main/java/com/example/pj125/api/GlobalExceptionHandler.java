package com.example.pj125.api;

import com.example.pj125.common.Ack;
import com.example.pj125.common.ApiException;
import com.example.pj125.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Ack<Void>> handleApi(ApiException e) {
        HttpStatus status = switch (e.getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Ack.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Ack<Void>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Ack.fail(ErrorCode.VALIDATION_ERROR, "参数校验失败"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Ack<Void>> handleViolation(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(Ack.fail(ErrorCode.VALIDATION_ERROR, "参数校验失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Ack<Void>> handleOther(Exception e) {
        return ResponseEntity.internalServerError().body(Ack.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误: " + e.getMessage()));
    }
}

