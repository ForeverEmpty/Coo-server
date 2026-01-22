package org.foreverempty.coofile.controller;

import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.Result;
import org.foreverempty.coofile.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Controller
@RequestMapping("")
public class FileController {
    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file){
        log.info(
                "Start processing file upload: Original filename={}, Size={} bytes",
                file.getOriginalFilename(),
                file.getSize()
        );
        try {
            String url = fileService.uploadFile(file);
            return Result.success(url);
        } catch (Exception e) {
            log.error("File upload fail! Error reason : {}", e.getMessage(), e);
            return Result.error("Upload fail" + e.getMessage());
        }
    }
}
