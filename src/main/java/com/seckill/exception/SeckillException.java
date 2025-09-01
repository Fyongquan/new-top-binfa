package com.seckill.exception;

/**
 * 秒杀业务异常类
 * 
 * @author seckill-test
 */
public class SeckillException extends RuntimeException {

  private Integer code;

  public SeckillException(String message) {
    super(message);
    this.code = 500;
  }

  public SeckillException(Integer code, String message) {
    super(message);
    this.code = code;
  }

  public SeckillException(String message, Throwable cause) {
    super(message, cause);
    this.code = 500;
  }

  public SeckillException(Integer code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public Integer getCode() {
    return code;
  }

  public void setCode(Integer code) {
    this.code = code;
  }
}
