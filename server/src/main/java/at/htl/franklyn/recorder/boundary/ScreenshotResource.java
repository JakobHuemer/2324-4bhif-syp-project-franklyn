package at.htl.franklyn.recorder.boundary;

import at.htl.franklyn.recorder.dto.IntervalUpdateDto;
import at.htl.franklyn.server.services.ScreenshotService;
import at.htl.franklyn.server.services.MetricsService;
import com.sun.tools.javac.Main;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.logging.Logger;

@Path("/screenshot")
public class ScreenshotResource {

    @ConfigProperty(name = "timestamp.pattern")
    String timestampPattern;

    @ConfigProperty(name = "screenshots.path")
    String screenshotsPath;

    @Inject
    SavesResource savesResource;

    @Inject
    ScreenshotService screenshotService;

    @Inject
    MetricsService metricsService;

    Logger logger = Logger.getLogger(getClass().getName());

    @POST
    @Path("{username}/alpha")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void takeAlphaScreenshot(@PathParam("username") String username,
                               @RestForm("image") @PartType(MediaType.APPLICATION_OCTET_STREAM) InputStream screenshot){
        io.micrometer.core.instrument.Timer.Sample sample = metricsService.startAlphaUploadTimer();
        long bytes = 0;
        try {
            File directory = Paths.get(screenshotsPath, username).toFile();

            if(!directory.exists()){
                directory.mkdirs();
            }

            byte[] data = screenshot.readAllBytes();
            bytes = data.length;
            BufferedImage alpha =  ImageIO.read(new ByteArrayInputStream(data));

            ImageIO.write(
                    alpha,
                    "png",
                    Paths.get(
                            directory.getPath(),
                            "/alphaframe.png"
                    ).toFile()
            );

            ImageIO.write(
                    alpha,
                    "png",
                    Paths.get(
                            directory.getPath(),
                            String.format("/%s-%s.png",
                                    username,
                                    new SimpleDateFormat(timestampPattern).format(new Date()))
                    ).toFile()
            );

            metricsService.recordAlphaUpload(sample, bytes);
        }
        catch (Exception e){
            metricsService.recordAlphaUploadError(sample);
            logger.warning("Type: " + e.getClass().getSimpleName());

            Arrays.stream(e.getStackTrace())
                    .filter(traceElement -> traceElement.getClassName().equals(getClass().getName()))
                    .forEach(t -> logger.warning(t.toString()));
        }
    }

    @POST
    @Path("{username}/beta")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void takeBetaScreenshot(@PathParam("username") String username,
                               @RestForm("image") @PartType(MediaType.APPLICATION_OCTET_STREAM) InputStream screenshot){

        BufferedImage alphaFrame = null;
        io.micrometer.core.instrument.Timer.Sample sample = metricsService.startBetaUploadTimer();
        long bytes = 0;
        long changedPixels = 0;

        try {
            File directory = Paths.get(screenshotsPath, username).toFile();

            if(!directory.exists()){
                directory.mkdirs();
            }

            byte[] diffData = screenshot.readAllBytes();
            bytes = diffData.length;
            BufferedImage diffFrame = ImageIO.read(new ByteArrayInputStream(diffData));

            alphaFrame = ImageIO.read(Paths
                    .get(
                            directory.getPath(),
                            "/alphaframe.png"
                    )
                    .toFile()
            );

            int width = alphaFrame.getWidth();
            int height = alphaFrame.getHeight();

            BufferedImage betaFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int alphaRGB = alphaFrame.getRGB(x, y);
                    int diffRGB = diffFrame.getRGB(x, y);

                    if (0 != diffRGB) {
                        betaFrame.setRGB(x, y, diffRGB);
                        changedPixels++;
                    } else {
                        betaFrame.setRGB(x, y, alphaRGB);
                    }
                }
            }

            ImageIO.write(
                    betaFrame,
                    "png",
                    Paths.get(
                            directory.getPath(),
                            String.format("/%s-%s.png",
                                    username,
                                    new SimpleDateFormat(timestampPattern).format(new Date()))
                    ).toFile()
            );

            metricsService.recordBetaUpload(sample, bytes, changedPixels);
        }
        catch (Exception e){
            metricsService.recordBetaUploadError(sample);
            if (alphaFrame == null) {
                screenshotService.requestAlphaFrame(username);
                metricsService.recordAlphaFrameRequestedFromClient();
                Log.warn("Had to request new alphaframe");
            } else {
                logger.warning("Type: " + e.getClass().getSimpleName());

                Arrays.stream(e.getStackTrace())
                        .filter(traceElement -> traceElement.getClassName().equals(getClass().getName()))
                        .forEach(t -> logger.warning(t.toString()));
            }
        }
    }

    @GET
    @Path("{username}")
    @Produces("image/png")
    public Response getScreenshot(@PathParam("username") String username) {
        io.micrometer.core.instrument.Timer.Sample sample = metricsService.startScreenshotFetchTimer();
        File directory = Paths.get(screenshotsPath, username).toFile();

        Response response = Response.status(404).entity(null).build();

        if(!directory.exists()){
            metricsService.recordScreenshotError(sample);
            return response;
        }

        File[] files = directory.listFiles();

        if(files == null){
            metricsService.recordScreenshotError(sample);
            return response;
        }

        try {
            File newestScreenshot = Collections.max( // errors here if there is no image
                Arrays.stream(files)
                        .filter(file -> file.getName().endsWith("png")) // has to be png
                        .filter(file -> !file.getName().startsWith("alphaframe")) // cant be alphaframe
                        .toList(),
                Comparator.comparing(File::getName)
            );

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            BufferedImage image = ImageIO.read(newestScreenshot);

            ImageIO.write(image, "png", byteArrayOutputStream);
            response = Response
                    .ok()
                    .entity(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))
                    .build();
            metricsService.recordScreenshotFetch(sample);
        } catch (Exception e) {
            metricsService.recordScreenshotError(sample);
            logger.warning("Type: " + e.getClass().getSimpleName());

            Arrays.stream(e.getStackTrace())
                    .filter(traceElement -> traceElement.getClassName().equals(getClass().getName()))
                    .forEach(t -> logger.warning(t.toString()));
        }

        return response;
    }

    @GET
    @Path("{username}/{width}/{height}")
    @Produces("image/png")
    public Response getScreenshotWithSpecificDimensions(
            @PathParam("username") String username,
            @PathParam("width") int width,
            @PathParam("height") int height)
    {
        io.micrometer.core.instrument.Timer.Sample sample = metricsService.startScreenshotScaledFetchTimer();
        Response response = Response.status(404).entity(null).build();

        try{
            BufferedImage screenShot = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            ByteArrayInputStream bis = (ByteArrayInputStream) getScreenshot(username).getEntity();

            if(bis == null)
            {
                metricsService.recordScreenshotError(sample);
                return response;
            }

            Image scaledImage = ImageIO.read(bis).getScaledInstance(width, height, Image.SCALE_SMOOTH);

            screenShot.getGraphics().drawImage(scaledImage, 0, 0, null);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            ImageIO.write(screenShot, "png", byteArrayOutputStream);
            response = Response
                    .ok()
                    .entity(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))
                    .build();
            metricsService.recordScreenshotScaledFetch(sample);
        }
        catch (Exception e){
            metricsService.recordScreenshotError(sample);
            logger.warning("Type: " + e.getClass().getSimpleName());

            Arrays.stream(e.getStackTrace())
                    .filter(traceElement -> traceElement.getClassName().equals(getClass().getName()))
                    .forEach(t -> logger.warning(t.toString()));
        }

        return response;
    }

    @POST
    @Path("updateInterval")
    public Response updateScreenshotInterval(IntervalUpdateDto intervalUpdateDto) {
        this.savesResource.rescheduleScreenshotUpdateJob(intervalUpdateDto.newInterval());
        return Response.ok().build();
    }

    @GET
    @Path("intervalSpeed")
    public Response getIntervalSpeed() {
        return Response.ok(this.savesResource.getIntervalSpeed()).build();
    }
}
