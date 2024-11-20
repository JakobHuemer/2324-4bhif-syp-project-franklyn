package at.htl.franklyn.server.feature.telemetry.video;

import at.htl.franklyn.server.common.Limits;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "f_video_job")
public class VideoJob {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "VJ_ID")
    private Long id;

    @NotNull(message = "VideoJob state can not be null")
    @Column(name = "VJ_STATE", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private VideoJobState state;

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

    public VideoJob(VideoJobState state, String artifactPath) {
        this.state = state;
        this.artifactPath = artifactPath;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public @NotNull(message = "VideoJob state can not be null") VideoJobState getState() {
        return state;
    }

    public void setState(@NotNull(message = "VideoJob state can not be null") VideoJobState state) {
        this.state = state;
    }

    public @Size(
            message = "Artifact path must have a length between "
                    + Limits.FILE_PATH_LENGTH_MIN + " and "
                    + Limits.PATH_LENGTH_MAX + " characters",
            min = Limits.FILE_PATH_LENGTH_MIN,
            max = Limits.PATH_LENGTH_MAX
    ) String getArtifactPath() {
        return artifactPath;
    }

    public void setArtifactPath(@Size(
            message = "Artifact path must have a length between "
                    + Limits.FILE_PATH_LENGTH_MIN + " and "
                    + Limits.PATH_LENGTH_MAX + " characters",
            min = Limits.FILE_PATH_LENGTH_MIN,
            max = Limits.PATH_LENGTH_MAX
    ) String artifactPath) {
        this.artifactPath = artifactPath;
    }

    @Override
    public String toString() {
        return "VideoJob{" +
                "id=" + id +
                ", state=" + state +
                ", artifactPath='" + artifactPath + '\'' +
                '}';
    }
}
