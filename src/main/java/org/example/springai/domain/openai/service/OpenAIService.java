package org.example.springai.domain.openai.service;

import org.example.springai.domain.openai.entity.ChatEntity;
import org.example.springai.domain.openai.repository.ChatRepository;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

@Service
public class OpenAIService {

    private final OpenAiChatModel openAiChatModel;
    private final OpenAiEmbeddingModel openAiEmbeddingModel;
    private final OpenAiImageModel openAiImageModel;
    private final OpenAiAudioSpeechModel openAiAudioSpeechModel;
    private final OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel;

    private final ChatMemoryRepository chatMemoryRepository;

    private final ChatRepository chatRepository;

    // 생성자
    public OpenAIService(OpenAiChatModel openAiChatModel, OpenAiEmbeddingModel openAiEmbeddingModel, OpenAiImageModel openAiImageModel, OpenAiAudioSpeechModel openAiAudioSpeechModel, OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel, ChatMemoryRepository chatMemoryRepository, ChatRepository chatRepository) {
        this.openAiChatModel = openAiChatModel;
        this.openAiEmbeddingModel = openAiEmbeddingModel;
        this.openAiImageModel = openAiImageModel;
        this.openAiAudioSpeechModel = openAiAudioSpeechModel;
        this.openAiAudioTranscriptionModel = openAiAudioTranscriptionModel;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatRepository = chatRepository;
    }

    // 1. Chat model: response(모든 응답 생성 후 전체 응답으로 반환)
    public String generate(String text) {
        // 메시지
        SystemMessage systemMessage = new SystemMessage("");
        UserMessage userMessage = new UserMessage(text);
        AssistantMessage assistantMessage = new AssistantMessage("");

        // 옵션
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("gpt-5-mini")
                .temperature(1.0)    // gpt-5 모델은 temperature 값 무조건 1.0
                .build();

        // 프롬프트
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage, assistantMessage), options);

        // 요청 및 응답
        ChatResponse response = openAiChatModel.call(prompt);

        return response.getResult().getOutput().getText();
    }

    // 1. Chat model: stream(토큰 단위로 생성되는 스트림 형태)
    public Flux<String> generateStream(String text) {
        // 유저 & 페이지 별 ChatMemory를 관리하기 위한 key (명시적)
        String userId = "xxxjjhhh" + "_" + "3";

        // 전체 대화 저장
        ChatEntity chatUserEntity = new ChatEntity();
        chatUserEntity.setUserId(userId);
        chatUserEntity.setType(MessageType.USER);
        chatUserEntity.setContent(text);

        // 메시지
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryRepository(chatMemoryRepository)
                .build();

        chatMemory.add(userId, new UserMessage(text));  // 신규 메시지도 추가

        // 옵션
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("gpt-5-mini")
                .temperature(1.0)    // gpt-5 모델은 temperature 값 무조건 1.0
                .build();

        // 프롬프트
        Prompt prompt = new Prompt(chatMemory.get(userId), options);

        // 응답 메시지를 저장할 임시 버퍼
        StringBuilder responseBuffer = new StringBuilder();

        return openAiChatModel.stream(prompt)
                .mapNotNull(response -> {
                    String token = response.getResult().getOutput().getText();

                    if (token != null) {    // null 응답 객체 제거
                        responseBuffer.append(token);
                        return token;
                    }

                    return null;
                })

                .doOnComplete(() -> {
                    chatMemory.add(userId, new AssistantMessage(responseBuffer.toString()));
                    chatMemoryRepository.saveAll(userId, chatMemory.get(userId));

                    // 전체 대화 저장
                    ChatEntity chatAssistantEntity = new ChatEntity();
                    chatAssistantEntity.setUserId(userId);
                    chatAssistantEntity.setType(MessageType.ASSISTANT);
                    chatAssistantEntity.setContent(responseBuffer.toString());

                    chatRepository.saveAll(List.of(chatUserEntity, chatAssistantEntity));
                });
    }

    // 2. Embedding model API 호출 메서드
    public List<float[]> generateEmbedding(List<String> texts, String model) {
        // 옵션
        EmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        // 프롬프트
        EmbeddingRequest prompt = new EmbeddingRequest(texts, embeddingOptions);

        // 요청 및 응답
        EmbeddingResponse response = openAiEmbeddingModel.call(prompt);

        return response.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
    }

    // 3. Image model API 호출 메서드
    public List<String> generateImage(String text, int count, int height, int width) {
        // 옵션
        OpenAiImageOptions imageOptions = OpenAiImageOptions.builder()
                .quality("hd")
                .N(count)
                .height(height)
                .width(width)
                .build();

        // 프롬프트
        ImagePrompt prompt = new ImagePrompt(text, imageOptions);

        // 요청 및 응답
        ImageResponse response = openAiImageModel.call(prompt);

        return response.getResults().stream()
                .map(image -> image.getOutput().getUrl())
                .toList();
    }

    // 4. Audio model APi 호출 메서드: TTS
    public byte[] tts(String text) {
        // 옵션
        OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
                .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                .speed(1.0)     // 파라미터 타입 float -> double
                .model(OpenAiAudioApi.TtsModel.TTS_1.value)
                .build();

        // 프롬프트
        // SpeechPrompt -> TextToSpeechPrompt
        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, speechOptions);

        // 요청 및 응답
        // SpeechResponse -> TextToSpeechResponse
        TextToSpeechResponse response = openAiAudioSpeechModel.call(prompt);

        return response.getResult().getOutput();
    }

    // 4. Audio model API 호출 메서드: STT
    public String stt(Resource audioFile) {
        // 옵션
        OpenAiAudioApi.TranscriptResponseFormat responseFormat = OpenAiAudioApi.TranscriptResponseFormat.VTT;
        OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .language("ko")     // 인식할 언어
                .prompt("Ask not this, but ask that")   // 음성 인식 전 참고할 텍스트 프롬프트
                .temperature(0f)
                .model(OpenAiAudioApi.TtsModel.TTS_1.value)
                .responseFormat(responseFormat)     // 결과 타입 지정: VTT 자막 형식
                .build();

        // 프롬프트
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);

        // 요청 및 응답
        AudioTranscriptionResponse response = openAiAudioTranscriptionModel.call(prompt);

        return response.getResult().getOutput();
    }
}
