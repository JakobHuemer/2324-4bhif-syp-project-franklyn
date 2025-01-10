package at.htl.franklyn.server.common;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@ApplicationScoped
public class InitBean {
    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @ConfigProperty(name = "screenshots.path")
    String screenshotsPath;

    @ConfigProperty(name = "video.path")
    String videosPath;

    void startUp(@Observes StartupEvent startupEvent) {
        Log.infof("Running on port: %d", port);

        // Create directory for screenshot storage if it does not exist already
        try {
            Files.createDirectories(Paths.get(screenshotsPath));
        } catch (IOException e) {
            Log.error("Could not create directory for screenshot storage: ", e);
        }

        // Create directory for video storage if it does not exist already
        try {
            Files.createDirectories(Paths.get(videosPath));
        } catch (IOException e) {
            Log.error("Could not create directory for video storage: ", e);
        }
    }
}
