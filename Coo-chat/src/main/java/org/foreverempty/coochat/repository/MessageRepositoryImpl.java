package org.foreverempty.coochat.repository;

import org.bson.Document;
import org.foreverempty.coochat.entity.ChatMessage;
import org.foreverempty.coochat.repository.model.RecentPrivateChatSession;
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

import java.util.ArrayList;
import java.util.List;

@Repository
public class MessageRepositoryImpl implements MessageRepositoryCustom {

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

        return mongoTemplate.find(query, ChatMessage.class);
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
        criteriaList.add(Criteria.where("toId").is(groupId));

        if (cursor != null) {
            criteriaList.add(Criteria.where("timestamp").lt(cursor));
        }

        Query query = Query.query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        return mongoTemplate.find(query, ChatMessage.class);
    }

    @Override
    public List<ChatMessage> queryGroupSharedMessages(String groupId, Integer contentType, Long cursor, int limit) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("chatType").is(2));
        criteriaList.add(Criteria.where("toId").is(groupId));
        criteriaList.add(Criteria.where("contentType").is(contentType));
        criteriaList.add(Criteria.where("status").ne(1));
        criteriaList.add(Criteria.where("url").ne(null));

        if (cursor != null) {
            criteriaList.add(Criteria.where("timestamp").lt(cursor));
        }

        Query query = Query.query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        return mongoTemplate.find(query, ChatMessage.class);
    }
}
