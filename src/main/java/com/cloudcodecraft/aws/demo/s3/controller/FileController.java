package com.cloudcodecraft.aws.demo.s3.controller;

import com.cloudcodecraft.aws.demo.s3.exception.ContentTypeNotAllowedException;
import com.cloudcodecraft.aws.demo.s3.service.FileService;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


@RestController
@RequestMapping("/file")
public class FileController {
    private final static Logger LOGGER = LoggerFactory.getLogger(FileController.class);
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * List all the files
     * @return
     */
    @PreAuthorize("hasAnyAuthority('file-service:admin','file-serive:list')")
    @GetMapping("")
    public ResponseEntity<?> getFileList() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(fileService.fileList());
    }

    /**
     * Read the content of the file
     * @param key file name
     * @param version specific version
     * @return
     * @throws ContentTypeNotAllowedException
     */
    @PreAuthorize("hasAnyAuthority('file-service:read', 'file-service:admin')")
    @GetMapping("/content")
    public ResponseEntity<?> getFileContent(@RequestParam("key") String key, @RequestParam("version") @Nullable String version) throws ContentTypeNotAllowedException {
        LOGGER.info("Request content {}", key);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(fileService.read(key, version));
    }

    /**
     * Upload a file
     * @param multipartFile
     * @return
     * @throws IOException
     */
    @PreAuthorize("hasAnyAuthority('file-service:write', 'file-service:admin')")
    @PostMapping("")
    public ResponseEntity<?> uploadFile(@RequestParam("file") @NotNull MultipartFile multipartFile) throws IOException {
        LOGGER.info(String.format("File upload request:%s, %s", multipartFile.getOriginalFilename(), multipartFile.getContentType()));
        return ResponseEntity.ok().body(fileService.upload(multipartFile));
    }

    /**
     * List all the version of the file
     * @param key
     * @return
     */
    @PreAuthorize("hasAnyAuthority('file-service:list', 'file-service:admin')")
    @GetMapping("/version")
    public ResponseEntity<?> getFileVersion(@RequestParam("key") @NotNull String key) {
        LOGGER.info("request parma: {}", key);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(fileService.versions(key));
    }

    /**
     * Delete file permanently
     * @param key
     * @param version
     * @return
     */
    @PreAuthorize("hasAnyAuthority('file-service:delete', 'file-service:admin')")
    @DeleteMapping("")
    public ResponseEntity<?> deleteFile(@RequestParam("key") String key, @RequestParam("version") @Nullable String version) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(fileService.delete(key, version));
    }
}
