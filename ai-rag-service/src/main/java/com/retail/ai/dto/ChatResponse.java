package com.retail.ai.dto;

import java.util.List;

public record ChatResponse(String answer, boolean grounded, List<Source> sources) {
    public record Source(String type, String sourceId, String title) {}
    public static ChatResponse abstain() {
        return new ChatResponse("Tôi chưa tìm thấy thông tin phù hợp trong dữ liệu hiện có.", false, List.of());
    }
}
