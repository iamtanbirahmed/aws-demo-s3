package com.cloudcodecraft.aws.demo.s3.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.Function;

public record VersionResponse(String name, String version, boolean latest, Instant lastModified) {
}