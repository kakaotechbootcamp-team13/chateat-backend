package com.chateat.chatEAT.domain.chat.repository;

import com.chateat.chatEAT.domain.chat.OutputChat;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OutputChatRepository extends MongoRepository<OutputChat, String> {
}
