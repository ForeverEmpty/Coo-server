package org.foreverempty.coochat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.foreverempty.common.model.ChatData;
import org.foreverempty.common.model.MessageModel;
import org.foreverempty.coochat.entity.ChatMessage;
import org.foreverempty.coochat.netty.SessionManager;
import org.foreverempty.coochat.repository.MessageRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    public void processChatMessage(MessageModel<ChatData> model) {
        ChatData data = model.getData();

        ChatMessage entity = new ChatMessage();
        BeanUtils.copyProperties(data, entity);

        entity.setId(model.getSequence());
        entity.setStatus(0);
        messageRepository.save(entity);
        log.info("save message: {}", entity.getId());

        if (data.getChatType() == 1) {
            String jsonStr = null;
            try {
                jsonStr = objectMapper.writeValueAsString(model);
                sessionManager.sendMessageToUser(Long.parseLong(data.getToId()), jsonStr);
            } catch (Exception e) {
                log.error("send message to user {} failed: {}", data.getToId(), e.getMessage());
            }
        }

        sendAck(Long.parseLong(data.getFromId()), model.getSequence());
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
}
