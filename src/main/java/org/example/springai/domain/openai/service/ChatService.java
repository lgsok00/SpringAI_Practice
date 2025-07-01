package org.example.springai.domain.openai.service;

import org.example.springai.domain.openai.entity.ChatEntity;
import org.example.springai.domain.openai.repository.ChatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatService {

    private final ChatRepository chatRepository;

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatEntity> readAllChats(String userId) {
        return chatRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }
}
