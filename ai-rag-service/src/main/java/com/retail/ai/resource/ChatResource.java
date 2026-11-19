package com.retail.ai.resource;

import com.retail.ai.dto.ChatRequest;
import com.retail.ai.dto.ChatResponse;
import com.retail.ai.service.AnswerService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Chatbot (public)")
public class ChatResource {
    @Inject AnswerService answer;

    @POST
    @Blocking
    @Operation(summary = "Ask AI Assistant (A1)", description = "Semantic retrieval + LLM synthesis with grounded citations")
    public ChatResponse chat(ChatRequest request) {
        return answer.answer(request == null ? null : request.question());
    }
}
