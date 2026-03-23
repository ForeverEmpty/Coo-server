package org.foreverempty.coochat.repository;

import org.bson.Document;
import org.foreverempty.common.model.ChatData;
import org.foreverempty.coochat.entity.ChatMessage;
import org.foreverempty.coochat.repository.model.RecentPrivateChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Repository
public class MessageRepositoryImpl implements MessageRepositoryCustom {
    private static final Logger log = LoggerFactory.getLogger(MessageRepositoryImpl.class);
    private static final DateTimeFormatter LEGACY_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<ChatMessage> queryPrivateHistory(String currentUserId, String peerId, Long cursor, int limit) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("chatType").is(1));
        criteriaList.add(new Criteria().orOperator(
                Criteria.where("fromId").is(currentUserId).and("toId").is(peerId),
                Criteria.where("fromId").is(peerId).and("toId").is(currentUserId)
        ));

        if (cursor != null) {
            criteriaList.add(Criteria.where("timestamp").lt(cursor));
        }

        Criteria finalCriteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        Query query = Query.query(finalCriteria)
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);

        return findChatMessagesSafely(query);
    }

    @Override
    public List<RecentPrivateChatSession> queryRecentPrivateSessions(String currentUserId, int limit) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("chatType").is(1),
                new Criteria().orOperator(
                        Criteria.where("fromId").is(currentUserId),
                        Criteria.where("toId").is(currentUserId)
                )
        );

        AddFieldsOperation addPeerId = Aggregation.addFields()
                .addField("peerId")
                .withValue(
                        ConditionalOperators.when(
                                        ComparisonOperators.valueOf("fromId").equalToValue(currentUserId)
                                )
                                .thenValueOf("toId")
                                .otherwiseValueOf("fromId")
                )
                .build();

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                addPeerId,
                Aggregation.sort(Sort.Direction.DESC, "timestamp"),
                Aggregation.group("peerId").first(Aggregation.ROOT).as("lastMessage"),
                Aggregation.project("lastMessage").and("_id").as("peerId"),
                Aggregation.sort(Sort.Direction.DESC, "lastMessage.timestamp"),
                Aggregation.limit(limit)
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation,
                mongoTemplate.getCollectionName(ChatMessage.class),
                Document.class
        );

        MongoConverter converter = mongoTemplate.getConverter();
        List<RecentPrivateChatSession> sessions = new ArrayList<>();
        for (Document row : results.getMappedResults()) {
            String peerId = row.getString("peerId");
            Document lastDoc = row.get("lastMessage", Document.class);
            if (peerId == null || lastDoc == null) {
                continue;
            }
            ChatMessage lastMessage = converter.read(ChatMessage.class, lastDoc);
            sessions.add(new RecentPrivateChatSession(peerId, lastMessage));
        }
        return sessions;
    }

    @Override
    public List<ChatMessage> queryGroupHistory(String groupId, Long cursor, int limit) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("chatType").is(2));
        criteriaList.add(buildGroupTargetCriteria(groupId));

        if (cursor != null) {
            criteriaList.add(Criteria.where("timestamp").lt(cursor));
        }

        Query query = Query.query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        return findChatMessagesSafely(query);
    }

    @Override
    public List<ChatMessage> queryGroupSharedMessages(String groupId, Integer contentType, Long cursor, int limit) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("chatType").is(2));
        criteriaList.add(buildGroupTargetCriteria(groupId));
        criteriaList.add(Criteria.where("contentType").is(contentType));
        criteriaList.add(Criteria.where("status").ne(1));
        criteriaList.add(Criteria.where("url").ne(null));

        if (cursor != null) {
            criteriaList.add(Criteria.where("timestamp").lt(cursor));
        }

        Query query = Query.query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        return findChatMessagesSafely(query);
    }

    private Criteria buildGroupTargetCriteria(String groupId) {
        if (!org.springframework.util.StringUtils.hasText(groupId)) {
            return Criteria.where("toId").is(groupId);
        }
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("toId").is(groupId));
        criteriaList.add(Criteria.where("toId").is("group_" + groupId));
        try {
            criteriaList.add(Criteria.where("toId").is(Long.parseLong(groupId)));
        } catch (NumberFormatException ignored) {
            // groupId is expected to be numeric, keep string compatible query only.
        }
        return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
    }

    private List<ChatMessage> findChatMessagesSafely(Query query) {
        try {
            return mongoTemplate.find(query, ChatMessage.class);
        } catch (Exception ex) {
            log.warn("fallback document mapping for chat history query, reason={}", ex.getMessage());
            String collection = mongoTemplate.getCollectionName(ChatMessage.class);
            List<Document> documents = mongoTemplate.find(query, Document.class, collection);
            List<ChatMessage> result = new ArrayList<>(documents.size());
            for (Document document : documents) {
                result.add(convertDocument(document));
            }
            return result;
        }
    }

    private ChatMessage convertDocument(Document document) {
        ChatMessage message = new ChatMessage();
        message.setId(readString(document, "_id"));
        message.setFromId(readString(document, "fromId"));
        message.setToId(readString(document, "toId"));
        message.setChatType(readInteger(document, "chatType"));
        message.setContentType(readInteger(document, "contentType"));
        message.setContent(readString(document, "content"));
        message.setUrl(readString(document, "url"));
        message.setFileName(readString(document, "fileName"));
        message.setFileSize(readLong(document, "fileSize"));
        message.setTimestamp(readTimestamp(document.get("timestamp")));
        message.setStatus(readInteger(document, "status"));
        message.setReplyTo(readReply(document.get("replyTo")));
        return message;
    }

    private String readString(Document document, String key) {
        Object value = document.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer readInteger(Document document, String key) {
        Object value = document.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long readLong(Document document, String key) {
        Object value = document.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long readTimestamp(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof Date date) {
            return date.getTime();
        }
        if (raw instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text, LEGACY_DATETIME).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private ChatData.ReplyModel readReply(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ChatData.ReplyModel replyModel) {
            return replyModel;
        }
        if (!(raw instanceof Document replyDocument)) {
            return null;
        }
        ChatData.ReplyModel replyModel = new ChatData.ReplyModel();
        replyModel.setMessageId(readString(replyDocument, "messageId"));
        replyModel.setSenderName(readString(replyDocument, "senderName"));
        replyModel.setContent(readString(replyDocument, "content"));
        if (Objects.isNull(replyModel.getMessageId())
                && Objects.isNull(replyModel.getSenderName())
                && Objects.isNull(replyModel.getContent())) {
            return null;
        }
        return replyModel;
    }
}
