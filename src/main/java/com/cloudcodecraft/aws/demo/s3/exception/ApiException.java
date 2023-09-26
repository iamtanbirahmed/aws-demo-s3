package com.cloudcodecraft.aws.demo.s3.exception;

import org.springframework.http.HttpStatus;

import java.time.ZonedDateTime;

public record ApiException(String message, HttpStatus httpStatus, int statuCode, ZonedDateTime zonedDateTime) {
}
