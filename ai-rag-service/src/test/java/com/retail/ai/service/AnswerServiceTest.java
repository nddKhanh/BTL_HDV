package com.retail.ai.service;

import com.retail.ai.dto.ChatResponse;
import com.retail.ai.exception.ApiProblem;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void answer_abstainsImmediatelyOnPriceAndStockKeywords() {
        when(state.ready()).thenReturn(true);

        List<String> priceStockQueries = List.of(
                "Áo thun giá bao nhiêu?",
                "Sản phẩm này bao nhiêu tiền?",
                "Giá hiện tại của balo?",
                "Cửa hàng còn hàng không?",
                "Tồn kho áo thun còn bao nhiêu?",
                "Còn mấy cái balo?"
        );

        for (String q : priceStockQueries) {
            ChatResponse resp = service.answer(q);
            assertFalse(resp.grounded(), "Should not be grounded for query: " + q);
            assertTrue(resp.sources().isEmpty());
            assertTrue(resp.answer().contains("Tôi chưa tìm thấy thông tin phù hợp"));
        }

        verifyNoInteractions(retrieval);
        verifyNoInteractions(model);
    }

    @Test
    void answer_abstainsWhenRetrievalMatchesIsEmpty() {
        when(state.ready()).thenReturn(true);
        when(retrieval.search(any())).thenReturn(List.of());

        ChatResponse resp = service.answer("Cửa hàng có máy bay phản lực không?");
        assertFalse(resp.grounded());
        assertTrue(resp.sources().isEmpty());
        verifyNoInteractions(model);
    }

    @Test
    void answer_returnsGroundedResponseWithDeduplicatedSources() {
        when(state.ready()).thenReturn(true);

        Metadata meta1 = Metadata.from(Map.of(
                "sourceType", "FAQ",
                "sourceId", "return-policy",
                "title", "Chính sách đổi trả"
        ));
        Metadata meta2 = Metadata.from(Map.of(
                "sourceType", "FAQ",
                "sourceId", "return-policy",
                "title", "Chính sách đổi trả"
        ));
        Metadata meta3 = Metadata.from(Map.of(
                "sourceType", "PRODUCT",
                "sourceId", "1111",
                "title", "Áo thun basic"
        ));

        TextSegment seg1 = TextSegment.from("Đổi trả trong 7 ngày", meta1);
        TextSegment seg2 = TextSegment.from("Phải còn nhãn mác", meta2);
        TextSegment seg3 = TextSegment.from("Cotton 100%", meta3);

        List<EmbeddingMatch<TextSegment>> matches = List.of(
                new EmbeddingMatch<>(0.9, "id1", null, seg1),
                new EmbeddingMatch<>(0.85, "id2", null, seg2),
                new EmbeddingMatch<>(0.8, "id3", null, seg3)
        );
        when(retrieval.search(any())).thenReturn(matches);

        dev.langchain4j.model.chat.response.ChatResponse lcResponse = mock(dev.langchain4j.model.chat.response.ChatResponse.class);
        when(lcResponse.aiMessage()).thenReturn(AiMessage.from("Sản phẩm hỗ trợ đổi trả trong 7 ngày còn nhãn."));
        when(model.chat(any(SystemMessage.class), any(UserMessage.class))).thenReturn(lcResponse);

        ChatResponse resp = service.answer("Chính sách đổi trả thế nào?");
        assertTrue(resp.grounded());
        assertEquals("Sản phẩm hỗ trợ đổi trả trong 7 ngày còn nhãn.", resp.answer());

        // Sources must be deduplicated by type:sourceId -> 2 sources (FAQ:return-policy and PRODUCT:1111)
        assertEquals(2, resp.sources().size());
        assertEquals("FAQ", resp.sources().get(0).type());
        assertEquals("return-policy", resp.sources().get(0).sourceId());
        assertEquals("PRODUCT", resp.sources().get(1).type());
        assertEquals("1111", resp.sources().get(1).sourceId());
    }

    @Test
    void answer_abstainsWhenModelSaysInformationNotFound() {
        when(state.ready()).thenReturn(true);

        Metadata meta = Metadata.from(Map.of(
                "sourceType", "FAQ",
                "sourceId", "faq-1",
                "title", "FAQ Tiêu đề"
        ));
        TextSegment seg = TextSegment.from("Nội dung", meta);
        when(retrieval.search(any())).thenReturn(List.of(new EmbeddingMatch<>(0.7, "id1", null, seg)));

        dev.langchain4j.model.chat.response.ChatResponse lcResponse = mock(dev.langchain4j.model.chat.response.ChatResponse.class);
        when(lcResponse.aiMessage()).thenReturn(AiMessage.from("Tôi chưa tìm thấy thông tin phù hợp trong dữ liệu hiện có."));
        when(model.chat(any(SystemMessage.class), any(UserMessage.class))).thenReturn(lcResponse);

        ChatResponse resp = service.answer("Câu hỏi lạ?");
        assertFalse(resp.grounded());
        assertTrue(resp.sources().isEmpty());
    }

    @Test
    void answer_throws503WhenModelFails() {
        when(state.ready()).thenReturn(true);

        Metadata meta = Metadata.from(Map.of(
                "sourceType", "FAQ",
                "sourceId", "faq-1",
                "title", "FAQ Tiêu đề"
        ));
        TextSegment seg = TextSegment.from("Nội dung", meta);
        when(retrieval.search(any())).thenReturn(List.of(new EmbeddingMatch<>(0.7, "id1", null, seg)));

        when(model.chat(any(SystemMessage.class), any(UserMessage.class)))
                .thenThrow(new RuntimeException("OpenAI quota exceeded"));

        ApiProblem ex = assertThrows(ApiProblem.class, () -> service.answer("Hỏi gì đó?"));
        assertEquals(503, ex.status);
        assertEquals("AI_UNAVAILABLE", ex.code);
    }
}
