package at.htl.franklyn.server.feature.telemetry.video;

import at.htl.franklyn.server.common.Limits;
import at.htl.franklyn.server.feature.exam.Exam;
import at.htl.franklyn.server.feature.examinee.Examinee;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "f_video_job")
public class VideoJob {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "VJ_ID")
    private Long id;

    @NotNull(message = "VideoJob queue timestamp can not be null")
    @Column(name = "VJ_QUEUE_TIMESTAMP", nullable = false)
    private LocalDateTime queueTimestamp;

    @NotNull(message = "VideoJob state can not be null")
    @Column(name = "VJ_STATE", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private VideoJobState state;

    @Column(name = "VJ_ERR_MSG", nullable = true)
    private String errorMessage;

    @NotNull(message = "VideoJob type can not be null")
    @Column(name = "VJ_TYPE", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private VideoJobType type;

    @NotNull(message = "VideoJob examId can not be null")
    @JoinColumn(name = "VJ_EXAM", nullable = false)
    @ManyToOne(cascade = {
            CascadeType.DETACH,
            CascadeType.MERGE,
            CascadeType.PERSIST,
            CascadeType.REFRESH
    }, optional = false)
    private Exam exam;

    @JoinColumn(name = "VJ_EXAMINEE", nullable = true)
    @ManyToOne(cascade = {
            CascadeType.DETACH,
            CascadeType.MERGE,
            CascadeType.PERSIST,
            CascadeType.REFRESH
    }, optional = true)
    private Examinee examinee;

    @NotNull(message = "created timestamp can not be null!")
    @Column(name = "VJ_CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "VJ_FINISHED_AT", nullable = true)
    private LocalDateTime finishedAt;

    @Size(
            message = "Artifact path must have a length between "
                    + Limits.FILE_PATH_LENGTH_MIN + " and "
                    + Limits.PATH_LENGTH_MAX + " characters",
            min = Limits.FILE_PATH_LENGTH_MIN,
            max = Limits.PATH_LENGTH_MAX
    )
    @Column(name = "VJ_ARTIFACT_PATH", nullable = true, length = Limits.PATH_LENGTH_MAX)
    private String artifactPath;

    public VideoJob() {
    }

    public VideoJob(LocalDateTime queueTimestamp, VideoJobState state, VideoJobType type, Exam exam, Examinee examinee, LocalDateTime createdAt, LocalDateTime finishedAt, String artifactPath, String errorMessage) {
        this.queueTimestamp = queueTimestamp;
        this.state = state;
        this.type = type;
        this.exam = exam;
        this.examinee = examinee;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
        this.artifactPath = artifactPath;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getQueueTimestamp() {
        return queueTimestamp;
    }

    public void setQueueTimestamp(LocalDateTime queueTimestamp) {
        this.queueTimestamp = queueTimestamp;
    }

    public VideoJobState getState() {
        return state;
    }

    public void setState(VideoJobState state) {
        this.state = state;
    }

    public VideoJobType getType() {
        return type;
    }

    public void setType(VideoJobType type) {
        this.type = type;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public Examinee getExaminee() {
        return examinee;
    }

    public void setExaminee(Examinee examinee) {
        this.examinee = examinee;
    }

    public String getArtifactPath() {
        return artifactPath;
    }

    public void setArtifactPath(String artifactPath) {
        this.artifactPath = artifactPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
