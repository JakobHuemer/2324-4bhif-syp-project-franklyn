package at.htl.franklyn.server.feature.telemetry.video;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VideoJobRepository implements PanacheRepository<VideoJob> {
}
