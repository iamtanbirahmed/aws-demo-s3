package com.cloudcodecraft.aws.demo.s3.exception;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

@ControllerAdvice
public class GlobalExceptionHandler {


    Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);



    @ExceptionHandler(value = {S3Exception.class})
    ResponseEntity<ApiException> handleS3Exception(S3Exception ex) {
        return handleException(() -> {
            return createApiException(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value());
        });
    }

    @ExceptionHandler(value = {IOException.class, ContentTypeNotAllowedException.class})
    ResponseEntity<ApiException> IOException(IOException ex) {
        return handleException(() -> createApiException(ex.getMessage(), HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value()));
    }

    private static ApiException createApiException(String message, HttpStatus status, int errorCode) {
        return new ApiException(
                message,
                status,
                errorCode,
                ZonedDateTime.now(ZoneId.of("Z"))
        );
    }

    private ResponseEntity<ApiException> handleException(Supplier<ApiException> apiExceptionSupplier) {
        ApiException apiException = apiExceptionSupplier.get();
        logger.error(apiException.toString());
        return ResponseEntity.status(apiException.statuCode()).body(apiException);
    }

}
