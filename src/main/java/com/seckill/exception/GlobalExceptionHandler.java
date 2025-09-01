package com.seckill.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 全局异常处理器
 * 
 * @author seckill-test
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 处理秒杀业务异常
   */
  @ExceptionHandler(SeckillException.class)
  public ResponseEntity<Map<String, Object>> handleSeckillException(SeckillException e) {
    log.error("秒杀业务异常: {}", e.getMessage(), e);

    Map<String, Object> response = new HashMap<>();
    response.put("code", e.getCode());
    response.put("message", e.getMessage());
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * 处理参数校验异常 - @Valid
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
    log.error("参数校验异常: {}", e.getMessage());

    Map<String, Object> response = new HashMap<>();
    Map<String, String> errors = new HashMap<>();

    for (FieldError error : e.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }

    response.put("code", 400);
    response.put("message", "参数校验失败");
    response.put("errors", errors);
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * 处理参数绑定异常
   */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<Map<String, Object>> handleBindException(BindException e) {
    log.error("参数绑定异常: {}", e.getMessage());

    Map<String, Object> response = new HashMap<>();
    Map<String, String> errors = new HashMap<>();

    for (FieldError error : e.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }

    response.put("code", 400);
    response.put("message", "参数绑定失败");
    response.put("errors", errors);
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * 处理约束违反异常 - @Validated
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException e) {
    log.error("约束违反异常: {}", e.getMessage());

    Map<String, Object> response = new HashMap<>();
    Map<String, String> errors = new HashMap<>();

    Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
    for (ConstraintViolation<?> violation : violations) {
      String propertyPath = violation.getPropertyPath().toString();
      String message = violation.getMessage();
      errors.put(propertyPath, message);
    }

    response.put("code", 400);
    response.put("message", "约束违反");
    response.put("errors", errors);
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * 处理非法参数异常
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
    log.error("非法参数异常: {}", e.getMessage());

    Map<String, Object> response = new HashMap<>();
    response.put("code", 400);
    response.put("message", "参数错误: " + e.getMessage());
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  /**
   * 处理空指针异常
   */
  @ExceptionHandler(NullPointerException.class)
  public ResponseEntity<Map<String, Object>> handleNullPointerException(NullPointerException e) {
    log.error("空指针异常", e);

    Map<String, Object> response = new HashMap<>();
    response.put("code", 500);
    response.put("message", "系统内部错误");
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  /**
   * 处理通用异常
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
    log.error("系统异常", e);

    Map<String, Object> response = new HashMap<>();
    response.put("code", 500);
    response.put("message", "系统异常: " + e.getMessage());
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}
