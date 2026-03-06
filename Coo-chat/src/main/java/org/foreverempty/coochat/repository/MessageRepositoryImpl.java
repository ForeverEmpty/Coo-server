package org.foreverempty.coochat.repository;

import org.foreverempty.coochat.entity.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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
}
