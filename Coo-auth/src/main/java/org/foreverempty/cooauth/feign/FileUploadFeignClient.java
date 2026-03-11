package org.foreverempty.cooauth.feign;

import org.foreverempty.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "Coo-file")
public interface FileUploadFeignClient {

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<String> upload(@RequestPart("file") MultipartFile file);
}

