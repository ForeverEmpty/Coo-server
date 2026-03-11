package org.foreverempty.coosocial.controller;

import org.foreverempty.common.PageResult;
import org.foreverempty.common.Result;
import org.foreverempty.coosocial.dto.GroupFileConfigUpdateDTO;
import org.foreverempty.coosocial.dto.GroupFileFolderCreateDTO;
import org.foreverempty.coosocial.dto.GroupFileFolderRenameDTO;
import org.foreverempty.coosocial.dto.GroupFileMoveDTO;
import org.foreverempty.coosocial.dto.GroupFileRenameDTO;
import org.foreverempty.coosocial.service.GroupFileService;
import org.foreverempty.coosocial.vo.GroupFileConfigVO;
import org.foreverempty.coosocial.vo.GroupFileFolderVO;
import org.foreverempty.coosocial.vo.GroupFileItemVO;
import org.foreverempty.coosocial.vo.GroupFileUploadVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/group/{groupId}/files")
public class GroupFileController {

    @Autowired
    private GroupFileService groupFileService;

    @GetMapping("/config")
    public Result<GroupFileConfigVO> getConfig(@PathVariable Long groupId) {
        return groupFileService.getConfig(groupId);
    }

    @PutMapping("/config")
    public Result<String> updateConfig(@PathVariable Long groupId,
                                       @RequestBody GroupFileConfigUpdateDTO dto) {
        return groupFileService.updateConfig(groupId, dto);
    }

    @GetMapping("/folders")
    public Result<List<GroupFileFolderVO>> listFolders(@PathVariable Long groupId,
                                                       @RequestParam(value = "parentId", required = false) Long parentId) {
        return groupFileService.listFolders(groupId, parentId);
    }

    @PostMapping("/folders")
    public Result<GroupFileFolderVO> createFolder(@PathVariable Long groupId,
                                                  @RequestBody GroupFileFolderCreateDTO dto) {
        return groupFileService.createFolder(groupId, dto);
    }

    @PutMapping("/folders/{folderId}/rename")
    public Result<String> renameFolder(@PathVariable Long groupId,
                                       @PathVariable Long folderId,
                                       @RequestBody GroupFileFolderRenameDTO dto) {
        return groupFileService.renameFolder(groupId, folderId, dto);
    }

    @PutMapping("/folders/{folderId}/move")
    public Result<String> moveFolder(@PathVariable Long groupId,
                                     @PathVariable Long folderId,
                                     @RequestBody GroupFileMoveDTO dto) {
        return groupFileService.moveFolder(groupId, folderId, dto);
    }

    @DeleteMapping("/folders/{folderId}")
    public Result<String> deleteFolder(@PathVariable Long groupId, @PathVariable Long folderId) {
        return groupFileService.deleteFolder(groupId, folderId);
    }

    @GetMapping("")
    public Result<PageResult<GroupFileItemVO>> listFiles(@PathVariable Long groupId,
                                                         @RequestParam(value = "folderId", required = false) Long folderId,
                                                         @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                                         @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return groupFileService.listFiles(groupId, folderId, pageNum, pageSize);
    }

    @PostMapping("/upload")
    public Result<GroupFileUploadVO> upload(@PathVariable Long groupId,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "folderId", required = false) Long folderId,
                                            @RequestParam(value = "source", required = false) String source,
                                            @RequestParam(value = "sourceMessageId", required = false) String sourceMessageId) {
        return groupFileService.uploadFile(groupId, folderId, source, sourceMessageId, file);
    }

    @PutMapping("/{fileId}/rename")
    public Result<String> renameFile(@PathVariable Long groupId,
                                     @PathVariable Long fileId,
                                     @RequestBody GroupFileRenameDTO dto) {
        return groupFileService.renameFile(groupId, fileId, dto);
    }

    @PutMapping("/{fileId}/move")
    public Result<String> moveFile(@PathVariable Long groupId,
                                   @PathVariable Long fileId,
                                   @RequestBody GroupFileMoveDTO dto) {
        return groupFileService.moveFile(groupId, fileId, dto);
    }

    @DeleteMapping("/{fileId}")
    public Result<String> deleteFile(@PathVariable Long groupId, @PathVariable Long fileId) {
        return groupFileService.deleteFile(groupId, fileId);
    }
}

