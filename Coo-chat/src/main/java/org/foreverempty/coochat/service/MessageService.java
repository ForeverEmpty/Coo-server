package org.foreverempty.coochat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.Result;
import org.foreverempty.common.constant.ContentType;
import org.foreverempty.common.constant.MessageType;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.model.ChatData;
import org.foreverempty.common.model.MessageModel;
import org.foreverempty.common.model.RecallData;
import org.foreverempty.coochat.dto.ChatHistoryQueryDTO;
import org.foreverempty.coochat.dto.RecallRequestDTO;
import org.foreverempty.coochat.entity.ChatMessage;
import org.foreverempty.coochat.feign.GroupChatFeignClient;
import org.foreverempty.coochat.feign.vo.GroupChatMemberAccessVO;
import org.foreverempty.coochat.netty.SessionManager;
import org.foreverempty.coochat.repository.MessageRepository;
import org.foreverempty.coochat.repository.model.RecentPrivateChatSession;
import org.foreverempty.coochat.vo.ChatHistoryCursorVO;
import org.foreverempty.coochat.vo.ChatHistoryMessageVO;
import org.foreverempty.coochat.vo.ChatRecentPrivateSessionVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class MessageService {
    private static final long PRIVATE_RECALL_WINDOW_MS = 2 * 60 * 1000L;
    private static final long GROUP_RECALL_WINDOW_MS = 2 * 60 * 1000L;
    private static final int PRIVATE_CHAT_TYPE = 1;
    private static final int GROUP_CHAT_TYPE = 2;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GroupChatFeignClient groupChatFeignClient;

    public void processChatMessage(MessageModel<ChatData> model, Long currentUserId) {
        if (model == null || model.getData() == null || currentUserId == null) {
            return;
        }

        ChatData data = model.getData();
        Integer chatType = data.getChatType();
        if (!Objects.equals(chatType, PRIVATE_CHAT_TYPE) && !Objects.equals(chatType, GROUP_CHAT_TYPE)) {
            return;
        }

        String sequence = resolveSequence(model.getSequence());
        long timestamp = System.currentTimeMillis();

        data.setFromId(String.valueOf(currentUserId));
        data.setTimestamp(timestamp);
        model.setSequence(sequence);
        model.setData(data);

        if (Objects.equals(chatType, GROUP_CHAT_TYPE)) {
            Long groupId = parseLong(data.getToId());
            if (groupId == null || !isGroupMember(groupId, currentUserId)) {
                log.warn("reject group message, sender is not member. groupId={}, userId={}", data.getToId(), currentUserId);
                return;
            }
        }

        ChatMessage entity = new ChatMessage();
        BeanUtils.copyProperties(data, entity);
        entity.setId(sequence);
        entity.setStatus(0);
        messageRepository.save(entity);

        if (Objects.equals(chatType, PRIVATE_CHAT_TYPE) && StringUtils.hasText(data.getToId())) {
            pushMessageToUser(parseLong(data.getToId()), model);
        }

        if (Objects.equals(chatType, GROUP_CHAT_TYPE)) {
            pushGroupMessage(data.getToId(), model);
        }

        sendAck(currentUserId, sequence);
    }

    public Result<ChatHistoryCursorVO> queryPrivateHistory(ChatHistoryQueryDTO queryDTO) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("Unauthorized");
        }

        String peerId = queryDTO != null ? queryDTO.getPeerId() : null;
        if (!StringUtils.hasText(peerId)) {
            return Result.error("peerId is required");
        }

        Long cursor = parseCursor(queryDTO != null ? queryDTO.getCursor() : null);
        if (Objects.equals(cursor, Long.MIN_VALUE)) {
            return Result.error("Invalid cursor");
        }

        int limit = normalizeLimit(queryDTO != null ? queryDTO.getLimit() : null);
        List<ChatMessage> raw = messageRepository.queryPrivateHistory(String.valueOf(currentUserId), peerId, cursor, limit + 1);
        return Result.success(toCursorResult(raw, limit));
    }

    public Result<ChatHistoryCursorVO> queryGroupHistory(ChatHistoryQueryDTO queryDTO) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("Unauthorized");
        }

        Long groupId = parseLong(queryDTO != null ? queryDTO.getGroupId() : null);
        if (groupId == null) {
            return Result.error("groupId is required");
        }
        if (!isGroupMember(groupId, currentUserId)) {
            return Result.error("No permission");
        }

        Long cursor = parseCursor(queryDTO != null ? queryDTO.getCursor() : null);
        if (Objects.equals(cursor, Long.MIN_VALUE)) {
            return Result.error("Invalid cursor");
        }

        int limit = normalizeLimit(queryDTO != null ? queryDTO.getLimit() : null);
        List<ChatMessage> raw = messageRepository.queryGroupHistory(String.valueOf(groupId), cursor, limit + 1);
        return Result.success(toCursorResult(raw, limit));
    }

    public Result<ChatHistoryCursorVO> queryGroupSharedImages(ChatHistoryQueryDTO queryDTO) {
        return queryGroupSharedMessages(queryDTO, ContentType.IMAGE.getCode());
    }

    public Result<ChatHistoryCursorVO> queryGroupSharedFiles(ChatHistoryQueryDTO queryDTO) {
        return queryGroupSharedMessages(queryDTO, ContentType.FILE.getCode());
    }

    public Result<List<ChatRecentPrivateSessionVO>> queryRecentPrivateSessions(Integer limitParam) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("Unauthorized");
        }

        int limit = normalizeLimit(limitParam == null ? 50 : limitParam);
        List<RecentPrivateChatSession> sessions = messageRepository.queryRecentPrivateSessions(String.valueOf(currentUserId), limit);

        List<ChatRecentPrivateSessionVO> result = new ArrayList<>(sessions.size());
        for (RecentPrivateChatSession session : sessions) {
            if (session.getLastMessage() == null) {
                continue;
            }
            ChatRecentPrivateSessionVO vo = new ChatRecentPrivateSessionVO();
            vo.setPeerId(session.getPeerId());

            ChatHistoryMessageVO messageVO = new ChatHistoryMessageVO();
            BeanUtils.copyProperties(session.getLastMessage(), messageVO);
            vo.setLastMessage(messageVO);
            result.add(vo);
        }

        return Result.success(result);
    }

    public Result<String> recallPrivateMessage(RecallRequestDTO requestDTO) {
        return recallMessage(requestDTO, false);
    }

    public Result<String> recallGroupMessage(RecallRequestDTO requestDTO) {
        return recallMessage(requestDTO, true);
    }

    private Result<ChatHistoryCursorVO> queryGroupSharedMessages(ChatHistoryQueryDTO queryDTO, int contentType) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("Unauthorized");
        }

        Long groupId = parseLong(queryDTO != null ? queryDTO.getGroupId() : null);
        if (groupId == null) {
            return Result.error("groupId is required");
        }
        if (!isGroupMember(groupId, currentUserId)) {
            return Result.error("No permission");
        }

        Long cursor = parseCursor(queryDTO != null ? queryDTO.getCursor() : null);
        if (Objects.equals(cursor, Long.MIN_VALUE)) {
            return Result.error("Invalid cursor");
        }

        int limit = normalizeLimit(queryDTO != null ? queryDTO.getLimit() : null);
        List<ChatMessage> raw = messageRepository.queryGroupSharedMessages(String.valueOf(groupId), contentType, cursor, limit + 1);
        return Result.success(toCursorResult(raw, limit));
    }

    private Result<String> recallMessage(RecallRequestDTO requestDTO, boolean groupChat) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("Unauthorized");
        }

        String messageId = requestDTO == null ? null : requestDTO.getMessageId();
        if (!StringUtils.hasText(messageId)) {
            return Result.error("messageId is required");
        }

        Optional<ChatMessage> optional = messageRepository.findById(messageId);
        if (optional.isEmpty()) {
            return Result.error("Message not found");
        }

        ChatMessage message = optional.get();
        int expectedChatType = groupChat ? GROUP_CHAT_TYPE : PRIVATE_CHAT_TYPE;
        if (!Objects.equals(message.getChatType(), expectedChatType)) {
            return Result.error(groupChat ? "Only group chat messages can be recalled" : "Only private chat messages can be recalled");
        }

        String operatorId = String.valueOf(currentUserId);
        boolean operatorIsSender = operatorId.equals(message.getFromId());
        boolean recallAnytime = false;
        if (groupChat) {
            Long groupId = parseLong(message.getToId());
            if (groupId == null) {
                return Result.error("Invalid group id");
            }
            GroupChatMemberAccessVO groupAccess = getGroupMemberAccess(groupId, currentUserId);
            if (groupAccess == null || !Boolean.TRUE.equals(groupAccess.getMember())) {
                return Result.error("No permission to recall this message");
            }
            recallAnytime = Boolean.TRUE.equals(groupAccess.getRecallAnytime());
            if (!operatorIsSender && !recallAnytime) {
                return Result.error("No permission to recall this message");
            }
        } else if (!operatorIsSender) {
            return Result.error("Only sender can recall this message");
        }

        if (Objects.equals(message.getStatus(), 1)) {
            return Result.success("Message recalled");
        }

        long messageTimestamp = message.getTimestamp() == null ? 0L : message.getTimestamp();
        if (groupChat) {
            if (!recallAnytime && operatorIsSender
                    && (messageTimestamp <= 0L || System.currentTimeMillis() - messageTimestamp > GROUP_RECALL_WINDOW_MS)) {
                return Result.error("Recall time window exceeded");
            }
        } else if (messageTimestamp <= 0L || System.currentTimeMillis() - messageTimestamp > PRIVATE_RECALL_WINDOW_MS) {
            return Result.error("Recall time window exceeded");
        }

        message.setStatus(1);
        messageRepository.save(message);

        RecallData notice = new RecallData();
        notice.setMessageId(message.getId());
        notice.setFromId(message.getFromId());
        notice.setToId(message.getToId());
        notice.setChatType(message.getChatType());
        notice.setOperatorId(operatorId);
        notice.setTimestamp(System.currentTimeMillis());

        MessageModel<RecallData> recallModel = new MessageModel<>();
        recallModel.setType(MessageType.RECALL.name());
        recallModel.setSequence(UUID.randomUUID().toString().replace("-", ""));
        recallModel.setData(notice);

        if (groupChat) {
            broadcastGroupRecall(message, recallModel);
        } else {
            try {
                String payload = objectMapper.writeValueAsString(recallModel);
                pushRawMessage(parseLong(message.getFromId()), payload);
                pushRawMessage(parseLong(message.getToId()), payload);
            } catch (Exception e) {
                log.warn("broadcast private recall notice failed: {}", e.getMessage());
            }
        }

        return Result.success("Message recalled");
    }

    private void pushGroupMessage(String groupIdText, MessageModel<ChatData> model) {
        Long groupId = parseLong(groupIdText);
        if (groupId == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(model);
            for (Long memberId : getGroupMemberIds(groupId)) {
                pushRawMessage(memberId, payload);
            }
        } catch (Exception e) {
            log.error("push group message failed, groupId={}: {}", groupId, e.getMessage());
        }
    }

    private void broadcastGroupRecall(ChatMessage message, MessageModel<RecallData> recallModel) {
        Long groupId = parseLong(message.getToId());
        if (groupId == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(recallModel);
            for (Long memberId : getGroupMemberIds(groupId)) {
                pushRawMessage(memberId, payload);
            }
        } catch (Exception e) {
            log.warn("broadcast group recall notice failed: {}", e.getMessage());
        }
    }

    private void pushMessageToUser(Long userId, MessageModel<ChatData> model) {
        if (userId == null) {
            return;
        }
        try {
            sessionManager.sendMessageToUser(userId, objectMapper.writeValueAsString(model));
        } catch (Exception e) {
            log.error("send message to user {} failed: {}", userId, e.getMessage());
        }
    }

    private void pushRawMessage(Long userId, String payload) {
        if (userId == null || !StringUtils.hasText(payload)) {
            return;
        }
        sessionManager.sendMessageToUser(userId, payload);
    }

    private boolean isGroupMember(Long groupId, Long userId) {
        GroupChatMemberAccessVO access = getGroupMemberAccess(groupId, userId);
        return access != null && Boolean.TRUE.equals(access.getMember());
    }

    private GroupChatMemberAccessVO getGroupMemberAccess(Long groupId, Long userId) {
        try {
            Result<GroupChatMemberAccessVO> result = groupChatFeignClient.getMemberAccess(groupId, userId);
            return result != null ? result.getData() : null;
        } catch (Exception e) {
            log.error("load group member access failed, groupId={}, userId={}", groupId, userId, e);
            return null;
        }
    }

    private List<Long> getGroupMemberIds(Long groupId) {
        try {
            Result<List<Long>> result = groupChatFeignClient.getMemberIds(groupId);
            List<Long> ids = result != null ? result.getData() : null;
            return ids != null ? ids : Collections.emptyList();
        } catch (Exception e) {
            log.error("load group member ids failed, groupId={}", groupId, e);
            return Collections.emptyList();
        }
    }

    private ChatHistoryCursorVO toCursorResult(List<ChatMessage> raw, int limit) {
        boolean hasMore = raw.size() > limit;
        List<ChatMessage> page = hasMore ? raw.subList(0, limit) : raw;

        List<ChatHistoryMessageVO> list = new ArrayList<>(page.size());
        for (ChatMessage message : page) {
            ChatHistoryMessageVO vo = new ChatHistoryMessageVO();
            BeanUtils.copyProperties(message, vo);
            list.add(vo);
        }

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            ChatMessage last = page.get(page.size() - 1);
            nextCursor = last.getTimestamp() == null ? null : String.valueOf(last.getTimestamp());
        }

        ChatHistoryCursorVO result = new ChatHistoryCursorVO();
        result.setList(list);
        result.setHasMore(hasMore);
        result.setNextCursor(nextCursor);
        return result;
    }

    private void sendAck(Long userId, String sequence) {
        MessageModel<String> ack = new MessageModel<>();
        ack.setType(MessageType.ACK.name());
        ack.setSequence(sequence);
        ack.setData("SENT");
        try {
            sessionManager.sendMessageToUser(userId, objectMapper.writeValueAsString(ack));
        } catch (Exception ignored) {
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        return Math.max(1, Math.min(100, limit));
    }

    private Long parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveSequence(String sequence) {
        if (StringUtils.hasText(sequence)) {
            return sequence;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
