package at.htl.franklyn.server.feature.exam;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ExamInfoDto(
        @JsonProperty("id")
        long id,
        @JsonProperty("planned_start")
        LocalDateTime plannedStart,
        @JsonProperty("planned_end")
        LocalDateTime plannedEnd,
        @JsonProperty("actual_start")
        LocalDateTime actualStart,
        @JsonProperty("actual_end")
        LocalDateTime actualEnd,
        @JsonProperty("title")
        String title,
        @JsonProperty("pin")
        int pin,
        @JsonProperty("state")
        ExamState state,
        @JsonProperty("screencapture_interval_seconds")
        long screencaptureInterval,
        @JsonProperty("registered_students_num")
        long registeredStudents
) {
}
