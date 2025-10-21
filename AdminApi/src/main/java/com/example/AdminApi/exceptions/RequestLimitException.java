package com.example.AdminApi.exceptions;

public class RequestLimitException extends RuntimeException {
  public RequestLimitException(String message) {
    super(message);
  }
}
