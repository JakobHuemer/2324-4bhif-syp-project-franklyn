package at.htl.franklyn.server.feature.telemetry.video;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VideoJobDto(
        Long id,
        VideoJobState state,
        @JsonProperty("exam_id")
        Long examId,
        @JsonProperty("examinee_id")
        Long examineeId
) {
}
