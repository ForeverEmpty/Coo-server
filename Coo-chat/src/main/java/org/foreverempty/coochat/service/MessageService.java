package org.foreverempty.coochat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.Result;
import org.foreverempty.common.context.UserContext;
import org.foreverempty.common.model.ChatData;
import org.foreverempty.common.model.MessageModel;
import org.foreverempty.coochat.dto.ChatHistoryQueryDTO;
import org.foreverempty.coochat.entity.ChatMessage;
import org.foreverempty.coochat.netty.SessionManager;
import org.foreverempty.coochat.repository.MessageRepository;
import org.foreverempty.coochat.vo.ChatHistoryCursorVO;
import org.foreverempty.coochat.vo.ChatHistoryMessageVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    public void processChatMessage(MessageModel<ChatData> model, Long currentUserId) {
        if (model == null || model.getData() == null || currentUserId == null) {
            return;
        }

        ChatData data = model.getData();
        String sequence = resolveSequence(model.getSequence());
        long timestamp = System.currentTimeMillis();

        data.setFromId(String.valueOf(currentUserId));
        data.setTimestamp(timestamp);
        model.setSequence(sequence);
        model.setData(data);

        ChatMessage entity = new ChatMessage();
        BeanUtils.copyProperties(data, entity);

        entity.setId(sequence);
        entity.setStatus(0);
        messageRepository.save(entity);
        log.info("save message: {}", entity.getId());

        if (Integer.valueOf(1).equals(data.getChatType()) && StringUtils.hasText(data.getToId())) {
            try {
                String jsonStr = objectMapper.writeValueAsString(model);
                sessionManager.sendMessageToUser(Long.parseLong(data.getToId()), jsonStr);
            } catch (Exception e) {
                log.error("send message to user {} failed: {}", data.getToId(), e.getMessage());
            }
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

        Long cursor = null;
        if (queryDTO != null && StringUtils.hasText(queryDTO.getCursor())) {
            try {
                cursor = Long.parseLong(queryDTO.getCursor());
            } catch (NumberFormatException e) {
                return Result.error("Invalid cursor");
            }
        }

        int limit = 20;
        if (queryDTO != null && queryDTO.getLimit() != null) {
            limit = Math.max(1, Math.min(100, queryDTO.getLimit()));
        }

        List<ChatMessage> raw = messageRepository.queryPrivateHistory(
                String.valueOf(currentUserId),
                peerId,
                cursor,
                limit + 1
        );

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
        return Result.success(result);
    }

    private void sendAck(Long userId, String sequence) {
        MessageModel<String> ack = new MessageModel<>();
        ack.setType("ACK");
        ack.setSequence(sequence);
        ack.setData("SENT");
        try {
            sessionManager.sendMessageToUser(userId, objectMapper.writeValueAsString(ack));
        } catch (Exception ignored) {}
    }

    private String resolveSequence(String sequence) {
        if (StringUtils.hasText(sequence)) {
            return sequence;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
