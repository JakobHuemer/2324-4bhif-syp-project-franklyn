package at.htl.franklyn.server.feature.telemetry.video;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record VideoJobDto(
        Long id,
        VideoJobState state,
        @JsonProperty("exam_id")
        Long examId,
        @JsonProperty("examinee_id")
        Long examineeId,
        @JsonProperty("created_at")
        LocalDateTime createdAt,
        @JsonProperty("finished_at")
        LocalDateTime finishedAt,
        @JsonProperty("error_message")
        String errorMessage
) {
}
