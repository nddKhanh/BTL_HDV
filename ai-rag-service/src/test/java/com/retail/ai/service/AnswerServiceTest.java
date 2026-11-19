package com.retail.ai.service;

import com.retail.ai.exception.ApiProblem;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnswerServiceTest {

    private IndexStateService state;
    private RetrievalService retrieval;
    private ChatModel model;
    private AnswerService service;

    @BeforeEach
    void setUp() {
        state = mock(IndexStateService.class);
        retrieval = mock(RetrievalService.class);
        model = mock(ChatModel.class);

        service = new AnswerService();
        service.state = state;
        service.retrieval = retrieval;
        service.model = model;
    }

    @Test
    void answer_throwsWhenQuestionIsBlankOrNullOrTooLong() {
        ApiProblem ex1 = assertThrows(ApiProblem.class, () -> service.answer(null));
        assertEquals(422, ex1.status);
        assertEquals("INVALID_QUESTION", ex1.code);

        ApiProblem ex2 = assertThrows(ApiProblem.class, () -> service.answer("   "));
        assertEquals(422, ex2.status);

        String tooLong = "q".repeat(1001);
        ApiProblem ex3 = assertThrows(ApiProblem.class, () -> service.answer(tooLong));
        assertEquals(422, ex3.status);
    }

    @Test
    void answer_throws503WhenIndexNotReady() {
        when(state.ready()).thenReturn(false);

        ApiProblem ex = assertThrows(ApiProblem.class, () -> service.answer("Chính sách là gì?"));
        assertEquals(503, ex.status);
        assertEquals("AI_UNAVAILABLE", ex.code);
        assertTrue(ex.getMessage().contains("not READY"));
    }
}
