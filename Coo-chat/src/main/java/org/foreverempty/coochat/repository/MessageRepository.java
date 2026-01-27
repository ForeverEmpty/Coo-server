package org.foreverempty.coochat.repository;

import org.foreverempty.coochat.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends MongoRepository<ChatMessage, String> {
}
