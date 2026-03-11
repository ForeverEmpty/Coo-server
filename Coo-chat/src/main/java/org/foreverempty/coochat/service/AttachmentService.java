package org.foreverempty.coochat.service;

import org.foreverempty.common.Result;
import org.foreverempty.coochat.feign.FileUploadFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Service
public class AttachmentService {
    private static final long MAX_UPLOAD_BYTES = 50L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/bmp",
            "application/pdf",
            "application/zip",
            "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
    );
    private static final Set<String> DENIED_EXTENSIONS = Set.of(
            "exe",
            "bat",
            "cmd",
            "com",
            "msi",
            "ps1",
            "sh"
    );

    @Autowired
    private FileUploadFeignClient fileUploadFeignClient;

    public Result<String> uploadAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("File is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return Result.error("File size exceeds 50MB");
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            return Result.error("Invalid file name");
        }
        String contentType = normalize(file.getContentType());
        String extension = extractExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return Result.error("Unsupported file type");
        }
        if (DENIED_EXTENSIONS.contains(extension)) {
            return Result.error("Forbidden file extension");
        }

        Result<String> result = fileUploadFeignClient.upload(file);
        if (result == null || !StringUtils.hasText(result.getData())) {
            return Result.error("Upload failed");
        }
        return Result.success(result.getData());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
