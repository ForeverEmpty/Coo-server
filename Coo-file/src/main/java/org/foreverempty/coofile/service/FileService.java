package org.foreverempty.coofile.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.foreverempty.coofile.config.MinioConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class FileService {
    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioConfig minioConfig;

    public String uploadFile(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String fileName = UUID.randomUUID()
                + "."
                + originalFilename.substring(originalFilename.lastIndexOf("."));

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(fileName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        return minioConfig.getEndpoint()
                + "/" + minioConfig.getBucketName()
                + "/" + fileName;
    }
}
