package at.htl.franklyn.server.common;

import jakarta.ws.rs.core.Application;
import java.util.TimeZone;

public class FranklynApplication extends Application {
    public FranklynApplication() {
        super();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
