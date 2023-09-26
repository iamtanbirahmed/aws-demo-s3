package com.cloudcodecraft.aws.demo.s3.exception;

import org.springframework.http.MediaType;

import java.io.IOException;

public class ContentTypeNotAllowedException extends IOException {
    public ContentTypeNotAllowedException() {
        super(String.format("Only %s Content-Type allowed", MediaType.TEXT_PLAIN));
    }
}
