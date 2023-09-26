package com.cloudcodecraft.aws.demo.s3.service;

import com.cloudcodecraft.aws.demo.s3.dto.ApiResponse;
import com.cloudcodecraft.aws.demo.s3.dto.ApiStatus;
import com.cloudcodecraft.aws.demo.s3.dto.VersionResponse;
import com.cloudcodecraft.aws.demo.s3.exception.ContentTypeNotAllowedException;
import io.awspring.cloud.s3.S3Operations;
import io.awspring.cloud.s3.S3Resource;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


@Service
public class FileService {


    private Predicate<String> fileExtensionFilter = (file) -> file != null;
    private Supplier<String> currentUser = () -> SecurityContextHolder.getContext().getAuthentication().getName();
    private Supplier<Map<String, String>> metadata = () -> {
        HashMap<String, String> md = new HashMap<>();
        md.put("version", "v1");
        md.put("owner", currentUser.get());
        md.put("timestamp", LocalDateTime.now().toString());
        return md;
    };
    private Function<String, String> mapFileName = (key) -> key.split("/")[1];
    private Function<S3Object, String> mapS3ObjectToFileName = (s3Object) -> mapFileName.apply(s3Object.key());

    private Function<String, String> buildPrefix = (key) -> currentUser.get() + "/" + key;
    private Function<ObjectVersion, VersionResponse> mapObjectVersionToVersionResponse = (version) -> new VersionResponse(mapFileName.apply(version.key()), version.versionId(), version.isLatest(), version.lastModified());
    private Map<String, Function<ResponseBytes<GetObjectResponse>, ApiResponse.ApiResponseBuilder>> fileHandlerMap;
    private final static Logger LOGGER = LoggerFactory.getLogger(FileService.class);


    @Value("${aws.s3.bucket}")
    private String BUCKET_NAME;

    @Value("${aws.s3.max-file-version}")
    private int MAX_VERSION;
    private final S3Client s3Client;
    private final S3Operations s3Operations;


    public FileService(S3Client s3Client, S3Operations s3Operations) {
        this.s3Client = s3Client;
        this.s3Operations = s3Operations;
        this.fileHandlerMap = Map.of("text/plain", handleTextFile, "image/png", handlePNGFile);
    }

    public ApiResponse fileList() {
        LOGGER.info("Fetching data from s3..");
        List<String> keys = this.s3Client.listObjects(request -> request.bucket(BUCKET_NAME).prefix(currentUser.get())) // Get all the files for the user
                .contents().stream().map(mapS3ObjectToFileName).filter(fileExtensionFilter) // remove the username from the file name
                .collect(Collectors.toList());
        return ApiResponse.builder().data(keys).status(ApiStatus.SUCCESS).metadata(Map.of("owner", currentUser.get(), "fileCount", String.valueOf(keys.size()))).build();
    }


    public ApiResponse upload(MultipartFile multipartFile) throws IOException {
        InputStream is = new BufferedInputStream(multipartFile.getInputStream());
        S3Resource upload = this.s3Operations.upload(BUCKET_NAME, buildPrefix.apply(multipartFile.getOriginalFilename()), is);
        LOGGER.info("File uploaded successfully..{}", upload.getLocation());

        Map<String, String> md = metadata.get();
        md.putAll(Map.of("fileType", upload.contentType()));
        return ApiResponse.builder().data(upload.getLocation()).status(ApiStatus.CREATED).metadata(md).build();
    }

    private Function<ResponseBytes<GetObjectResponse>, ApiResponse.ApiResponseBuilder> handleTextFile = (responseBytes) -> {
        ApiResponse.ApiResponseBuilder apiResponseBuilder = ApiResponse.builder();
        apiResponseBuilder.data(new String(responseBytes.asByteArray(), StandardCharsets.UTF_8));
        return apiResponseBuilder;
    };

    private Function<ResponseBytes<GetObjectResponse>, ApiResponse.ApiResponseBuilder> handlePNGFile = (responseBytes) -> {
        ApiResponse.ApiResponseBuilder apiResponseBuilder = ApiResponse.builder();
        apiResponseBuilder.data(responseBytes.asByteArray());
        return apiResponseBuilder;
    };

    private ResponseBytes<GetObjectResponse> readWithVersion(String key, @Nullable String version) {
        GetObjectRequest.Builder getObjectRequestBuilder = GetObjectRequest.builder();
        getObjectRequestBuilder
                .bucket(BUCKET_NAME)
                .key(buildPrefix.apply(key));
        if (version != null) getObjectRequestBuilder.versionId(version);
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(getObjectRequestBuilder.build());
        LOGGER.info("Object response {}", response.toString());
        return response;
    }


    public ApiResponse read(String key, @Nullable String version) throws ContentTypeNotAllowedException {
        Assert.notNull(key, "Key is required");
        ResponseBytes<GetObjectResponse> response = readWithVersion(key, version);
        final String FILE_TYPE = response.response().contentType();
        if (fileHandlerMap.containsKey(FILE_TYPE) == false) throw new ContentTypeNotAllowedException();
        return fileHandlerMap.get(FILE_TYPE)
                .apply(response)
                .status(ApiStatus.SUCCESS)
                .metadata(Map.of("version", "v1", "fileType", FILE_TYPE, "owner", currentUser.get()))
                .build();
    }

    public ApiResponse versions(String key) {
        Assert.notNull(key, "key is required");
        ListObjectVersionsRequest listObjectsRequest = ListObjectVersionsRequest.builder()
                .bucket(BUCKET_NAME)
                .prefix(buildPrefix.apply(key))
                .maxKeys(MAX_VERSION)
                .build();
        ListObjectVersionsResponse response = this.s3Client.listObjectVersions(listObjectsRequest);
        LOGGER.info("Getting all version of {}", key);
        List<VersionResponse> versionList = new LinkedList<>();
        LOGGER.info("Found {} versions for {}", response.versions().size(), key);
        response.versions().stream().map(mapObjectVersionToVersionResponse).forEach(vr -> versionList.add(vr));

        return ApiResponse.builder()
                .status(ApiStatus.SUCCESS)
                .data(versionList)
                .metadata(Map.of("owner", currentUser.get(), "versionCount", String.valueOf(versionList.size())))
                .build();
    }

    private String findLatestVersion(String key) {
        ListObjectVersionsRequest listObjectVersionsRequest = ListObjectVersionsRequest.builder().bucket(BUCKET_NAME).prefix(buildPrefix.apply(key)).build();
        ListObjectVersionsResponse response = s3Client.listObjectVersions(listObjectVersionsRequest);
        String latestVersion = response.versions().stream().filter(ObjectVersion::isLatest).map(ver -> ver.versionId()).findFirst().orElseThrow(() -> new NoSuchElementException());
        LOGGER.info("Getting the latest version prefix: {},  version: {}", key, latestVersion);
        return latestVersion;
    }


    private String deleteWithVersion(String key, @Nullable String version) {
        DeleteObjectResponse response = s3Client.deleteObject((request) -> request.bucket(BUCKET_NAME).key(buildPrefix.apply(key)).versionId(version).build());
        LOGGER.info("Deleting file {} {}", key, response.versionId());
        return response.responseMetadata().toString();
    }

    public ApiResponse delete(String key, @Nullable String version) {
        Assert.notNull(key, "key is required");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "v1");
        metadata.put("owner", currentUser.get());
        metadata.put("timestamp", LocalDateTime.now().toString());
        ApiResponse.ApiResponseBuilder apiResponseBuilder = ApiResponse.builder();
        apiResponseBuilder.status(ApiStatus.DELETE);

        if (version != null) {
            metadata.put("versionCount", String.valueOf(1));
            return apiResponseBuilder.data(deleteWithVersion(key, version)).metadata(metadata).build();
        }
        ListObjectVersionsResponse response = null;
        String nextKeyMarker = null;
        String nextVersionMarker = null;
        int totalVersionCount = 0;
        List<String> deletedVersions = new LinkedList<>();
        do {

            ListObjectVersionsRequest listObjectVersionsRequest = ListObjectVersionsRequest.builder()
                    .bucket(BUCKET_NAME)
                    .prefix(buildPrefix.apply(key))
                    .keyMarker(nextKeyMarker)
                    .versionIdMarker(nextVersionMarker)
                    .maxKeys(MAX_VERSION).build();

            response = this.s3Client.listObjectVersions(listObjectVersionsRequest);
            totalVersionCount += response.versions().size();

            response.versions()
                    .stream().map(vr -> vr.versionId())
                    .forEach(vid -> {
                        deletedVersions.add(vid);
                        LOGGER.info("{}", deleteWithVersion(key, vid));
                    });

            nextKeyMarker = response.nextKeyMarker();
            nextVersionMarker = response.nextVersionIdMarker();

        } while (response.isTruncated());

        metadata.put("versionCount", String.valueOf(totalVersionCount));
        return ApiResponse.builder().data(deletedVersions).status(ApiStatus.DELETE).metadata(metadata).build();
    }

}
