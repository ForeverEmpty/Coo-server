package org.foreverempty.coosocial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.PageResult;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.coosocial.content.GroupPermission;
import org.foreverempty.coosocial.dto.GroupFileConfigUpdateDTO;
import org.foreverempty.coosocial.dto.GroupFileFolderCreateDTO;
import org.foreverempty.coosocial.dto.GroupFileFolderRenameDTO;
import org.foreverempty.coosocial.dto.GroupFileMoveDTO;
import org.foreverempty.coosocial.dto.GroupFileRenameDTO;
import org.foreverempty.coosocial.entity.Group;
import org.foreverempty.coosocial.entity.GroupFileFolder;
import org.foreverempty.coosocial.entity.GroupFileItem;
import org.foreverempty.coosocial.entity.GroupMember;
import org.foreverempty.coosocial.entity.GroupTitle;
import org.foreverempty.coosocial.feign.FileUploadFeignClient;
import org.foreverempty.coosocial.mapper.GroupFileFolderMapper;
import org.foreverempty.coosocial.mapper.GroupFileItemMapper;
import org.foreverempty.coosocial.mapper.GroupMapper;
import org.foreverempty.coosocial.mapper.GroupMemberMapper;
import org.foreverempty.coosocial.mapper.GroupTitleMapper;
import org.foreverempty.coosocial.vo.GroupFileConfigVO;
import org.foreverempty.coosocial.vo.GroupFileFolderVO;
import org.foreverempty.coosocial.vo.GroupFileItemVO;
import org.foreverempty.coosocial.vo.GroupFileUploadVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GroupFileService {
    private static final int DEFAULT_FILE_CAPACITY_MB = 1024;
    private static final int DEFAULT_OVERSIZE_THRESHOLD_MB = 100;
    private static final int DEFAULT_TEMP_EXPIRE_DAYS = 7;
    private static final long MAX_UPLOAD_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final Set<String> DENIED_EXTENSIONS = Set.of(
            "exe",
            "bat",
            "cmd",
            "com",
            "msi",
            "ps1",
            "sh"
    );
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    private GroupMapper groupMapper;
    @Autowired
    private GroupMemberMapper groupMemberMapper;
    @Autowired
    private GroupTitleMapper groupTitleMapper;
    @Autowired
    private GroupFileFolderMapper groupFileFolderMapper;
    @Autowired
    private GroupFileItemMapper groupFileItemMapper;
    @Autowired
    private FileUploadFeignClient fileUploadFeignClient;

    public Result<GroupFileConfigVO> getConfig(Long groupId) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_VIEW)) {
            return Result.error("No permission");
        }
        return Result.success(toConfigVO(access.group));
    }

    @Transactional
    public Result<String> updateConfig(Long groupId, GroupFileConfigUpdateDTO dto) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_MANAGE_STORAGE)) {
            return Result.error("No permission");
        }
        if (dto == null) {
            return Result.error("Invalid payload");
        }

        Integer capacityMb = normalizePositive(dto.getFileCapacityMb(), DEFAULT_FILE_CAPACITY_MB);
        Integer oversizeMb = normalizePositive(dto.getOversizeThresholdMb(), DEFAULT_OVERSIZE_THRESHOLD_MB);
        Integer expireDays = normalizePositive(dto.getTempExpireDays(), DEFAULT_TEMP_EXPIRE_DAYS);
        if (capacityMb < 1 || oversizeMb < 1 || expireDays < 1) {
            return Result.error("Config must be positive");
        }

        access.group.setFileCapacityMb(capacityMb);
        access.group.setOversizeThresholdMb(oversizeMb);
        access.group.setTempExpireDays(expireDays);
        access.group.setUpdateTime(LocalDateTime.now());
        groupMapper.updateById(access.group);
        return Result.success("Group file config updated");
    }

    public Result<List<GroupFileFolderVO>> listFolders(Long groupId, Long parentId) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_VIEW)) {
            return Result.error("No permission");
        }

        long actualParentId = parentId == null ? 0L : parentId;
        List<GroupFileFolder> folders = groupFileFolderMapper.selectList(new LambdaQueryWrapper<GroupFileFolder>()
                .eq(GroupFileFolder::getGroupId, groupId)
                .eq(GroupFileFolder::getParentId, actualParentId)
                .eq(GroupFileFolder::getDeleted, 0)
                .orderByAsc(GroupFileFolder::getCreateTime));

        List<GroupFileFolderVO> result = folders.stream().map(item -> {
            GroupFileFolderVO vo = new GroupFileFolderVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).toList();
        return Result.success(result);
    }

    @Transactional
    public Result<GroupFileFolderVO> createFolder(Long groupId, GroupFileFolderCreateDTO dto) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_MANAGE)) {
            return Result.error("No permission");
        }
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            return Result.error("Folder name is required");
        }

        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId > 0 && !existsFolder(groupId, parentId)) {
            return Result.error("Parent folder not found");
        }

        GroupFileFolder folder = new GroupFileFolder();
        folder.setGroupId(groupId);
        folder.setParentId(parentId);
        folder.setName(dto.getName().trim());
        folder.setCreateBy(access.userId);
        folder.setDeleted(0);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());
        groupFileFolderMapper.insert(folder);

        GroupFileFolderVO vo = new GroupFileFolderVO();
        BeanUtils.copyProperties(folder, vo);
        return Result.success(vo);
    }

    @Transactional
    public Result<String> renameFolder(Long groupId, Long folderId, GroupFileFolderRenameDTO dto) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_MANAGE)) {
            return Result.error("No permission");
        }
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            return Result.error("Folder name is required");
        }
        GroupFileFolder folder = getFolder(groupId, folderId);
        if (folder == null) {
            return Result.error("Folder not found");
        }
        folder.setName(dto.getName().trim());
        folder.setUpdateTime(LocalDateTime.now());
        groupFileFolderMapper.updateById(folder);
        return Result.success("Folder renamed");
    }

    @Transactional
    public Result<String> moveFolder(Long groupId, Long folderId, GroupFileMoveDTO dto) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_MANAGE)) {
            return Result.error("No permission");
        }
        if (dto == null) {
            return Result.error("Invalid payload");
        }
        GroupFileFolder folder = getFolder(groupId, folderId);
        if (folder == null) {
            return Result.error("Folder not found");
        }
        Long targetParentId = dto.getTargetFolderId() == null ? 0L : dto.getTargetFolderId();
        if (targetParentId.equals(folderId)) {
            return Result.error("Cannot move folder into itself");
        }
        if (targetParentId > 0) {
            GroupFileFolder targetParent = getFolder(groupId, targetParentId);
            if (targetParent == null) {
                return Result.error("Target folder not found");
            }
            Set<Long> descendants = collectFolderIds(groupId, folderId);
            if (descendants.contains(targetParentId)) {
                return Result.error("Cannot move folder into its descendant");
            }
        }

        folder.setParentId(targetParentId);
        folder.setUpdateTime(LocalDateTime.now());
        groupFileFolderMapper.updateById(folder);
        return Result.success("Folder moved");
    }

    @Transactional
    public Result<String> deleteFolder(Long groupId, Long folderId) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_MANAGE)) {
            return Result.error("No permission");
        }
        GroupFileFolder folder = getFolder(groupId, folderId);
        if (folder == null) {
            return Result.error("Folder not found");
        }

        Set<Long> folderIds = collectFolderIds(groupId, folderId);
        if (folderIds.isEmpty()) {
            return Result.success("Folder deleted");
        }
        List<GroupFileItem> items = groupFileItemMapper.selectList(new LambdaQueryWrapper<GroupFileItem>()
                .eq(GroupFileItem::getGroupId, groupId)
                .in(GroupFileItem::getFolderId, folderIds)
                .eq(GroupFileItem::getDeleted, 0));

        long release = items.stream()
                .map(GroupFileItem::getChargedBytes)
                .filter(value -> value != null && value > 0L)
                .mapToLong(Long::longValue)
                .sum();

        LocalDateTime now = LocalDateTime.now();
        for (GroupFileItem item : items) {
            item.setDeleted(1);
            item.setDeletedAt(now);
            item.setUpdateTime(now);
            groupFileItemMapper.updateById(item);
        }

        List<GroupFileFolder> folders = groupFileFolderMapper.selectBatchIds(folderIds);
        for (GroupFileFolder item : folders) {
            item.setDeleted(1);
            item.setUpdateTime(now);
            groupFileFolderMapper.updateById(item);
        }

        releaseStorage(access.group, release);
        return Result.success("Folder deleted");
    }

    public Result<PageResult<GroupFileItemVO>> listFiles(Long groupId, Long folderId, Integer pageNum, Integer pageSize) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_VIEW)) {
            return Result.error("No permission");
        }

        int p = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        long offset = (long) (p - 1) * size;
        long actualFolderId = folderId == null ? 0L : folderId;

        List<GroupFileItem> all = groupFileItemMapper.selectList(new LambdaQueryWrapper<GroupFileItem>()
                .eq(GroupFileItem::getGroupId, groupId)
                .eq(GroupFileItem::getFolderId, actualFolderId)
                .eq(GroupFileItem::getDeleted, 0)
                .orderByDesc(GroupFileItem::getCreateTime));

        long total = all.size();
        List<GroupFileItem> page = all.stream()
                .skip(offset)
                .limit(size)
                .toList();
        List<GroupFileItemVO> list = page.stream().map(this::toFileVO).toList();
        return Result.success(new PageResult<>(list, total, p, size, offset + size < total));
    }

    @Transactional
    public Result<GroupFileUploadVO> uploadFile(Long groupId,
                                                Long folderId,
                                                String source,
                                                String sourceMessageId,
                                                MultipartFile file) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_UPLOAD)) {
            return Result.error("No permission");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("File is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return Result.error("File size exceeds 512MB");
        }
        String fileName = normalizeFileName(file.getOriginalFilename());
        if (!StringUtils.hasText(fileName)) {
            return Result.error("Invalid file name");
        }
        if (DENIED_EXTENSIONS.contains(extractExtension(fileName))) {
            return Result.error("Forbidden file extension");
        }

        long actualFolderId = folderId == null ? 0L : folderId;
        if (actualFolderId > 0 && !existsFolder(groupId, actualFolderId)) {
            return Result.error("Folder not found");
        }

        long fileSize = file.getSize();
        long capacityBytes = mbToBytes(defaultValue(access.group.getFileCapacityMb(), DEFAULT_FILE_CAPACITY_MB));
        long thresholdBytes = mbToBytes(defaultValue(access.group.getOversizeThresholdMb(), DEFAULT_OVERSIZE_THRESHOLD_MB));
        long usedBytes = defaultValue(access.group.getUsedStorageBytes(), 0L);
        long remaining = Math.max(0L, capacityBytes - usedBytes);
        boolean oversizeTemp = fileSize > remaining && fileSize > thresholdBytes;

        if (!oversizeTemp && fileSize > remaining) {
            return Result.error("Group file storage is full");
        }

        Result<String> uploadResult = fileUploadFeignClient.upload(file);
        String url = uploadResult != null ? uploadResult.getData() : null;
        if (!StringUtils.hasText(url)) {
            return Result.error("Upload failed");
        }

        LocalDateTime now = LocalDateTime.now();
        GroupFileItem item = new GroupFileItem();
        item.setGroupId(groupId);
        item.setFolderId(actualFolderId);
        item.setFileName(fileName);
        item.setUrl(url);
        item.setObjectKey(extractObjectKey(url));
        item.setFileSize(fileSize);
        item.setMimeType(file.getContentType());
        item.setSource(normalizeSource(source));
        item.setSourceMessageId(trimToNull(sourceMessageId));
        item.setIsTemp(oversizeTemp ? 1 : 0);
        item.setExpireAt(oversizeTemp ? now.plusDays(defaultValue(access.group.getTempExpireDays(), DEFAULT_TEMP_EXPIRE_DAYS)) : null);
        item.setChargedBytes(oversizeTemp ? 0L : fileSize);
        item.setDeleted(0);
        item.setCreateBy(access.userId);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        groupFileItemMapper.insert(item);

        if (!oversizeTemp) {
            access.group.setUsedStorageBytes(usedBytes + fileSize);
            access.group.setUpdateTime(now);
            groupMapper.updateById(access.group);
        }

        GroupFileUploadVO vo = new GroupFileUploadVO();
        vo.setFileId(item.getId());
        vo.setUrl(url);
        vo.setFileName(fileName);
        vo.setFileSize(fileSize);
        vo.setTemp(oversizeTemp);
        vo.setExpireAt(item.getExpireAt());
        return Result.success(vo);
    }

    @Transactional
    public Result<String> renameFile(Long groupId, Long fileId, GroupFileRenameDTO dto) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_MANAGE)) {
            return Result.error("No permission");
        }
        if (dto == null || !StringUtils.hasText(dto.getFileName())) {
            return Result.error("File name is required");
        }
        GroupFileItem item = getFile(groupId, fileId);
        if (item == null) {
            return Result.error("File not found");
        }
        item.setFileName(dto.getFileName().trim());
        item.setUpdateTime(LocalDateTime.now());
        groupFileItemMapper.updateById(item);
        return Result.success("File renamed");
    }

    @Transactional
    public Result<String> moveFile(Long groupId, Long fileId, GroupFileMoveDTO dto) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_MANAGE)) {
            return Result.error("No permission");
        }
        if (dto == null) {
            return Result.error("Invalid payload");
        }
        GroupFileItem item = getFile(groupId, fileId);
        if (item == null) {
            return Result.error("File not found");
        }
        long targetFolderId = dto.getTargetFolderId() == null ? 0L : dto.getTargetFolderId();
        if (targetFolderId > 0 && !existsFolder(groupId, targetFolderId)) {
            return Result.error("Target folder not found");
        }
        item.setFolderId(targetFolderId);
        item.setUpdateTime(LocalDateTime.now());
        groupFileItemMapper.updateById(item);
        return Result.success("File moved");
    }

    @Transactional
    public Result<String> deleteFile(Long groupId, Long fileId) {
        AccessContext access = loadAccess(groupId);
        if (access.error != null) {
            return Result.error(access.error);
        }
        if (!hasPermission(access, GroupPermission.GROUP_FILE_MANAGE)) {
            return Result.error("No permission");
        }
        GroupFileItem item = getFile(groupId, fileId);
        if (item == null) {
            return Result.error("File not found");
        }
        if (item.getDeleted() != null && item.getDeleted() == 1) {
            return Result.success("File deleted");
        }
        item.setDeleted(1);
        item.setDeletedAt(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        groupFileItemMapper.updateById(item);
        releaseStorage(access.group, defaultValue(item.getChargedBytes(), 0L));
        return Result.success("File deleted");
    }

    @Scheduled(cron = "0 0/30 * * * ?")
    @Transactional
    public void softDeleteExpiredTempFiles() {
        LocalDateTime now = LocalDateTime.now();
        List<GroupFileItem> expired = groupFileItemMapper.selectList(new LambdaQueryWrapper<GroupFileItem>()
                .eq(GroupFileItem::getDeleted, 0)
                .eq(GroupFileItem::getIsTemp, 1)
                .isNotNull(GroupFileItem::getExpireAt)
                .le(GroupFileItem::getExpireAt, now));
        if (expired.isEmpty()) {
            return;
        }
        for (GroupFileItem item : expired) {
            item.setDeleted(1);
            item.setDeletedAt(now);
            item.setUpdateTime(now);
            groupFileItemMapper.updateById(item);
        }
        log.info("soft deleted expired group temp files, count={}", expired.size());
    }

    private GroupFileItemVO toFileVO(GroupFileItem item) {
        GroupFileItemVO vo = new GroupFileItemVO();
        BeanUtils.copyProperties(item, vo);
        vo.setTemp(item.getIsTemp() != null && item.getIsTemp() == 1);
        return vo;
    }

    private boolean existsFolder(Long groupId, Long folderId) {
        return getFolder(groupId, folderId) != null;
    }

    private GroupFileFolder getFolder(Long groupId, Long folderId) {
        if (folderId == null || folderId <= 0L) {
            return null;
        }
        return groupFileFolderMapper.selectOne(new LambdaQueryWrapper<GroupFileFolder>()
                .eq(GroupFileFolder::getId, folderId)
                .eq(GroupFileFolder::getGroupId, groupId)
                .eq(GroupFileFolder::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private GroupFileItem getFile(Long groupId, Long fileId) {
        if (fileId == null) {
            return null;
        }
        return groupFileItemMapper.selectOne(new LambdaQueryWrapper<GroupFileItem>()
                .eq(GroupFileItem::getId, fileId)
                .eq(GroupFileItem::getGroupId, groupId)
                .eq(GroupFileItem::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private Set<Long> collectFolderIds(Long groupId, Long rootFolderId) {
        Set<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(rootFolderId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (current == null || current <= 0 || !result.add(current)) {
                continue;
            }
            List<GroupFileFolder> children = groupFileFolderMapper.selectList(new LambdaQueryWrapper<GroupFileFolder>()
                    .eq(GroupFileFolder::getGroupId, groupId)
                    .eq(GroupFileFolder::getParentId, current)
                    .eq(GroupFileFolder::getDeleted, 0)
                    .select(GroupFileFolder::getId));
            for (GroupFileFolder child : children) {
                queue.addLast(child.getId());
            }
        }
        return result;
    }

    private void releaseStorage(Group group, long bytes) {
        if (group == null || bytes <= 0L) {
            return;
        }
        long used = defaultValue(group.getUsedStorageBytes(), 0L);
        group.setUsedStorageBytes(Math.max(0L, used - bytes));
        group.setUpdateTime(LocalDateTime.now());
        groupMapper.updateById(group);
    }

    private GroupFileConfigVO toConfigVO(Group group) {
        GroupFileConfigVO vo = new GroupFileConfigVO();
        int capacityMb = defaultValue(group.getFileCapacityMb(), DEFAULT_FILE_CAPACITY_MB);
        int oversizeThresholdMb = defaultValue(group.getOversizeThresholdMb(), DEFAULT_OVERSIZE_THRESHOLD_MB);
        int tempExpireDays = defaultValue(group.getTempExpireDays(), DEFAULT_TEMP_EXPIRE_DAYS);
        long used = defaultValue(group.getUsedStorageBytes(), 0L);
        long capacityBytes = mbToBytes(capacityMb);
        vo.setFileCapacityMb(capacityMb);
        vo.setOversizeThresholdMb(oversizeThresholdMb);
        vo.setTempExpireDays(tempExpireDays);
        vo.setUsedStorageBytes(used);
        vo.setRemainingStorageBytes(Math.max(0L, capacityBytes - used));
        return vo;
    }

    private AccessContext loadAccess(Long groupId) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return AccessContext.error("Unauthorized");
        }
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return AccessContext.error("Group not found");
        }
        GroupMember member = groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, currentUserId)
                .last("LIMIT 1"));
        if (member == null) {
            return AccessContext.error("No permission");
        }
        Set<String> permissions = resolvePermissions(group, member);
        return AccessContext.ok(currentUserId, group, member, permissions);
    }

    private Set<String> resolvePermissions(Group group, GroupMember member) {
        if (group != null && group.getOwnerId() != null && group.getOwnerId().equals(member.getUserId())) {
            return Arrays.stream(GroupPermission.values()).map(Enum::name).collect(Collectors.toSet());
        }
        if (member.getTitleId() == null) {
            return new HashSet<>(Collections.singletonList(GroupPermission.GROUP_VIEW.name()));
        }
        GroupTitle title = groupTitleMapper.selectById(member.getTitleId());
        if (title == null || !StringUtils.hasText(title.getPermissions())) {
            return new HashSet<>(Collections.singletonList(GroupPermission.GROUP_VIEW.name()));
        }
        try {
            List<String> list = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(title.getPermissions(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            HashSet<String> permissions = new HashSet<>(list);
            permissions.add(GroupPermission.GROUP_VIEW.name());
            return permissions;
        } catch (Exception e) {
            log.warn("parse group permissions failed, groupId={}, titleId={}", group.getId(), title.getId(), e);
            return new HashSet<>(Collections.singletonList(GroupPermission.GROUP_VIEW.name()));
        }
    }

    private boolean hasPermission(AccessContext access, GroupPermission permission) {
        return access.permissions.contains(permission.name());
    }

    private String extractObjectKey(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "MANUAL";
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "MANUAL";
        }
        if ("MANUAL".equals(normalized) || "CHAT_MESSAGE".equals(normalized)) {
            return normalized;
        }
        return "MANUAL";
    }

    private long mbToBytes(int mb) {
        return mb * 1024L * 1024L;
    }

    private Integer normalizePositive(Integer value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int defaultValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private long defaultValue(Long value, long defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String normalizeFileName(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return null;
        }
        String fileName = originalName.trim();
        if (fileName.isEmpty()) {
            return null;
        }
        fileName = fileName.replace("\\", "/");
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        fileName = fileName.trim();
        if (fileName.isEmpty() || fileName.length() > MAX_FILE_NAME_LENGTH) {
            return null;
        }
        return fileName;
    }

    private String extractExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    @Data
    private static class AccessContext {
        private Long userId;
        private Group group;
        private GroupMember member;
        private Set<String> permissions;
        private String error;

        static AccessContext error(String error) {
            AccessContext context = new AccessContext();
            context.setError(error);
            return context;
        }

        static AccessContext ok(Long userId, Group group, GroupMember member, Set<String> permissions) {
            AccessContext context = new AccessContext();
            context.setUserId(userId);
            context.setGroup(group);
            context.setMember(member);
            context.setPermissions(permissions);
            return context;
        }
    }
}
