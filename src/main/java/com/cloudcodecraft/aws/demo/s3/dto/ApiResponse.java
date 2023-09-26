package com.cloudcodecraft.aws.demo.s3.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;


@Builder
@ToString
@Getter
@Setter
public class ApiResponse {

    // TODO implement a builder pattern with the response
    String status;
    Object data;
    Map<String, String> metadata;
}
