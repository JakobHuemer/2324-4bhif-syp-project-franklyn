package at.htl.franklyn.server.exam;

import at.htl.franklyn.server.feature.exam.ExamDto;
import at.htl.franklyn.server.feature.exam.ExamInfoDto;
import at.htl.franklyn.server.feature.exam.ExamState;
import at.htl.franklyn.server.feature.examinee.ExamineeDto;
import at.htl.franklyn.server.feature.telemetry.video.VideoJobDto;
import at.htl.franklyn.server.feature.telemetry.video.VideoJobState;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExamLifecycleTest {
    private static final String BASE_URL = "exams";
    private static final String JOIN_URL = "join";

    private static ExamInfoDto createdExam;
    private static String userSession;
    private static long joinedExamineeId;

    @Test
    @Order(0)
    void test_simpleCreateValidExam_ok() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().plusYears(2);
        LocalDateTime end = LocalDateTime.now().plusYears(3);
        String title = "test1";
        long interval = 5L;

        ExamDto examDto = new ExamDto(
            title,
            start,
            end,
            interval
        );

        // Act
        Response response = given()
                .contentType(ContentType.JSON)
                .body(examDto)
                .basePath(BASE_URL)
            .when()
                .post();

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.CREATED);

        ExamInfoDto exam = response.then()
                .log().body()
                .extract().as(ExamInfoDto.class);

        assertThat(response.header("Location"))
                .contains(BASE_URL)
                .contains(Long.toString(exam.id()));

        assertThat(exam.actualEnd())
                .isNull();
        assertThat(exam.actualStart())
                .isNull();
        assertThat(exam.pin().length())
                .isEqualTo(3);
        assertThat(exam.plannedStart())
                .isEqualTo(start);
        assertThat(exam.plannedEnd())
                .isEqualTo(end);
        assertThat(exam.state())
                .isEqualTo(ExamState.CREATED);
        assertThat(exam.title())
                .isEqualTo(title);
        assertThat(exam.screencaptureInterval())
                .isEqualTo(interval);

        createdExam = exam;
    }

    @Test
    @Order(100)
    void test_simpleGetExamById_ok() {
        // Arrange
        // created Exam is taken from the post test with @Order(1)

        // Act
        Response response = given()
                .basePath(BASE_URL)
            .when()
                .get(Long.toString(createdExam.id()));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        ExamInfoDto actualExam = response.then()
                .log().body()
                .extract().as(ExamInfoDto.class);

        assertThat(actualExam)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(LocalDateTime.class)
                .isEqualTo(createdExam);

        assertThat(actualExam.plannedStart())
                .isCloseTo(createdExam.plannedStart(), within(1, ChronoUnit.MINUTES));

        assertThat(actualExam.plannedEnd())
                .isCloseTo(createdExam.plannedEnd(), within(1, ChronoUnit.MINUTES));
    }

    @Test
    @Order(200)
    void test_simpleGetAllExams_ok() {
        // Arrange
        // created Exam is taken from the post test with @Order(1)
        ExamInfoDto expectedExam1 = new ExamInfoDto(
                1,
                LocalDateTime.of(2024, 10, 17, 10, 1),
                LocalDateTime.of(2024, 10, 17, 12, 30),
                null,
                null,
                "test",
                "123",
                ExamState.ONGOING,
                5,
                1
        );

        // Act
        Response response = given()
                .basePath(BASE_URL)
            .when()
                .get();

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        ExamInfoDto[] exams = response.then()
                .log().body()
                .extract().as(ExamInfoDto[].class);

        assertThat(exams)
                .hasSize(2);

        RecursiveComparisonConfiguration configuration = RecursiveComparisonConfiguration.builder()
                .withEqualsForType(
                        (actualDate, expectedDate) ->
                                ChronoUnit.MINUTES.between(actualDate, expectedDate) < 1, LocalDateTime.class)
                .withIgnoredFields("actualStart", "actualEnd")
                .withIgnoredFieldsOfTypes(LocalDateTime.class)
                .withIgnoredFieldsMatchingRegexes(".*hibernate.*")
                .build();

        assertThat(exams)
                .usingRecursiveComparison(configuration)
                .isEqualTo(new ExamInfoDto[] { expectedExam1, createdExam });
    }

    @Test
    @Order(300)
    void test_simpleGetExamineesOfExam_ok() {
        // Arrange
        ExamineeDto expectedExaminee = new ExamineeDto(
                "Max",
                "Mustermann",
                true,
                1L
        );

        // Act
        Response response = given()
                .basePath(BASE_URL)
            .when()
                .get(String.format("%d/examinees", 1)); // import sql creates exam with id 1

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        ExamineeDto[] examinees = response.then()
                .log().body()
                .extract().as(ExamineeDto[].class);

        assertThat(examinees)
                .hasSize(1);

        ExamineeDto actualExaminee = examinees[0];

        assertThat(actualExaminee)
                .usingRecursiveComparison()
                .ignoringFields("isConnected") // Potential race with cleanup job
                .isEqualTo(expectedExaminee);
    }

    @Test
    @Order(400)
    void test_simpleJoinExamWithValidUser_ok() {
        // Arrange
        ExamineeDto examineeDto = new ExamineeDto(
            "Test",
            "User",
            false, // value of is_connected does not matter on connection and is ignored
            0L
        );

        // Act
        Response response = given()
                .contentType(ContentType.JSON)
                .body(examineeDto)
                .basePath(BASE_URL)
            .when()
                .log().body()
                .post(String.format("%s/%s", JOIN_URL, createdExam.pin()));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.CREATED);

        assertThat(response.header("Location"))
                .matches(".*connect/.*");

        String[] parts = response.header("Location").split("/");
        // Location header has url to connect endpoint
        // The last part of that url is the sessionId
        userSession = parts[parts.length - 1];
    }

    @Test
    @Order(401)
    void test_getConnectionStateOfJoinedExaminee_ok() {
        // Arrange
        ExamineeDto expectedExaminee = new ExamineeDto(
                "Test",
                "User",
                false,
                51L
        );

        // Act
        Response response = given()
                .basePath(BASE_URL)
                .when()
                .get(String.format("%d/examinees", createdExam.id())); // import sql creates exam with id 1

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        ExamineeDto[] examinees = response.then()
                .log().body()
                .extract().as(ExamineeDto[].class);

        assertThat(examinees)
                .hasSize(1);

        ExamineeDto actualExaminee = examinees[0];

        assertThat(actualExaminee)
                .usingRecursiveComparison()
                .isEqualTo(expectedExaminee);

        joinedExamineeId = actualExaminee.id();
    }

    @Test
    @Order(500)
    void test_simpleTryUploadAlphaTooEarly_ok() {
        // Arrange
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("/alpha-frame.png")).getFile());

        // Act
        Response response = given()
                .contentType(ContentType.MULTIPART)
                .basePath("/telemetry")
                .multiPart("image", file)
                .when()
                .post(String.format("/by-session/%s/screen/upload/alpha", userSession));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(600)
    void test_simpleStartExam_ok() {
        // Arrange
        // created Exam is taken from the post test with @Order(1)

        // Act
        Response startResponse = given()
                .basePath(BASE_URL)
            .when()
                .post(String.format("%s/start", createdExam.id()));

        Response response = given()
                .basePath(BASE_URL)
            .when()
                .get(Long.toString(createdExam.id()));

        // Assert
        assertThat(startResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        ExamInfoDto actualExam = response.then()
                .log().body()
                .extract().as(ExamInfoDto.class);

        assertThat(actualExam.actualStart())
                .isNotNull();
        assertThat(actualExam.state())
                .isEqualTo(ExamState.ONGOING);
    }

    @Test
    @Order(601)
    void test_downloadLatestFrameTooEarly_ok() {
        // Arrange

        // Act
        Response response = given()
                .contentType(ContentType.JSON)
                .basePath("/telemetry")
                .when()
                .get(String.format("/by-user/%s/%s/screen/download", createdExam.id(), joinedExamineeId));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.NOT_FOUND);
    }

    @Test
    @Order(700)
    void test_simpleUploadAlpha_ok() {
        // Arrange
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("/alpha-frame.png")).getFile());

        // Act
        Response response = given()
                .contentType(ContentType.MULTIPART)
                .basePath("/telemetry")
                .multiPart("image", file)
            .when()
                .post(String.format("/by-session/%s/screen/upload/alpha", userSession));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);
    }

    @Test
    @Order(701)
    void test_uploadBadlySizedAlpha_ok() {
        // Arrange
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("/alpha-frame-1x1.png")).getFile());

        // Act
        Response response = given()
                .contentType(ContentType.MULTIPART)
                .basePath("/telemetry")
                .multiPart("image", file)
                .when()
                .post(String.format("/by-session/%s/screen/upload/alpha", userSession));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(701)
    void test_downloadLatestFrame_ok() throws IOException {
        // Arrange
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("alpha-frame.png")).getFile());

        // reading and writing using imageio seems to alter the png a bit
        // which is suboptimal for comparison
        // this hack replicates the actions done on the franklyn server to get byte perfect results
        BufferedImage img = ImageIO.read(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        // Act
        Response response = given()
                .contentType(ContentType.JSON)
                .basePath("/telemetry")
                .when()
                .get(String.format("/by-user/%s/%s/screen/download", createdExam.id(), joinedExamineeId));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);
        assertThat(response.body().asByteArray())
                .isEqualTo(imageBytes);
    }

    @Test
    @Order(800)
    void test_simpleUploadBeta_ok() {
        // Arrange
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("beta-frame.png")).getFile());

        // Act
        Response response = given()
                .contentType(ContentType.MULTIPART)
                .basePath("/telemetry")
                .multiPart("image", file)
                .when()
                .post(String.format("/by-session/%s/screen/upload/beta", userSession));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);
    }

    @Test
    @Order(801)
    void testuploadBadlySizedBeta_ok() {
        // Arrange
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("beta-frame-1x1.png")).getFile());

        // Act
        Response response = given()
                .contentType(ContentType.MULTIPART)
                .basePath("/telemetry")
                .multiPart("image", file)
                .when()
                .post(String.format("/by-session/%s/screen/upload/beta", userSession));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(900)
    void test_simpleCompleteExam_ok() {
        // Arrange
        // created Exam is taken from the post test with @Order(1)

        // Act
        Response completeResponse = given()
                .basePath(BASE_URL)
                .when()
                .post(String.format("%s/complete", createdExam.id()));

        Response response = given()
                .basePath(BASE_URL)
                .when()
                .get(Long.toString(createdExam.id()));

        // Assert
        assertThat(completeResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        ExamInfoDto actualExam = response.then()
                .log().body()
                .extract().as(ExamInfoDto.class);

        assertThat(actualExam.actualEnd())
                .isNotNull();
        assertThat(actualExam.state())
                .isEqualTo(ExamState.DONE);
    }

    @Test
    @Order(1000)
    void test_allExamineesDisconnectedAfterComplete_ok() {
        // Arrange
        // created Exam is taken from the post test with @Order(1)

        // Act
        Response examineesReponse = given()
                .basePath(BASE_URL)
                .when()
                .get(String.format("%s/examinees", createdExam.id()));

        // Assert
        assertThat(examineesReponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        ExamineeDto[] examinees = examineesReponse.then()
                .log().body()
                .extract().as(ExamineeDto[].class);

        assertThat(examinees)
                .hasSize(1);

        for (ExamineeDto examinee : examinees) {
            assertThat(examinee.isConnected())
                    .isFalse();
        }
    }

    @Test
    @Order(1100)
    void test_simpleTryUploadAlphaTooLate_ok() {
        // Arrange
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(Objects.requireNonNull(classLoader.getResource("/alpha-frame.png")).getFile());

        // Act
        Response response = given()
                .contentType(ContentType.MULTIPART)
                .basePath("/telemetry")
                .multiPart("image", file)
                .when()
                .post(String.format("/by-session/%s/screen/upload/alpha", userSession));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(1200)
    void test_downloadSingleVideo_ok() throws InterruptedException {
        // Arrange

        // Act: Start video job
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .basePath("/telemetry")
                .when()
                .post(String.format("/by-user/%d/%d/video/generate", joinedExamineeId, createdExam.id()));

        // Assert
        assertThat(createResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.ACCEPTED);
        VideoJobDto jobDto = createResponse.then()
                .log().body()
                .extract().as(VideoJobDto.class);
        assertThat(jobDto)
                .isNotNull();

        // Act: Poll for job progress
        do {
            Response pollResponse = given()
                    .contentType(ContentType.JSON)
                    .basePath("/telemetry")
                    .when()
                    .get(String.format("/jobs/video/%d", jobDto.id()));
            assertThat(pollResponse.statusCode())
                    .isEqualTo(RestResponse.StatusCode.OK);
            jobDto = pollResponse.then()
                    .log().body()
                    .extract().as(VideoJobDto.class);

            // Delay before requesting again
            Thread.sleep(500);
        } while(jobDto.state() == VideoJobState.QUEUED || jobDto.state() == VideoJobState.ONGOING);

        // Assert
        assertThat(jobDto.state())
                .isEqualTo(VideoJobState.DONE);


        // Act: Download video job artifact
        Response downloadResponse = given()
                .contentType(ContentType.JSON)
                .basePath("/telemetry")
                .when()
                .get(String.format("/jobs/video/%d/download", jobDto.id()));

        // Assert
        assertThat(downloadResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        byte[] video = downloadResponse.asByteArray();

        assertThat(video)
                .isNotNull();
        assertThat(video)
                .isNotEmpty();

    }

    @Test
    @Order(1201)
    void test_startBatchVideoGeneration_ok(){
        // Arrange

        // Act: Start video job
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .basePath("/telemetry")
                .when()
                .post(String.format("/by-exam/%d/video/generate-all", createdExam.id()));

        // Assert
        assertThat(createResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.ACCEPTED);
        VideoJobDto jobDto = createResponse.then()
                .log().body()
                .extract().as(VideoJobDto.class);
        assertThat(jobDto)
                .isNotNull();
        assertThat(jobDto.state())
                .isEqualTo(VideoJobState.QUEUED);
    }

    @Test
    @Order(1202)
    void test_startSingleVideoGenerationWithInvalidExaminee_ok() {
        // Arrange

        // Act: Start video job
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .basePath("/telemetry")
                .when()
                .post(String.format("/by-user/%d/%d/video/generate", -120, createdExam.id()));

        // Assert
        assertThat(createResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(1203)
    void test_startSingleVideoGenerationWithInvalidExam_ok() {
        // Arrange

        // Act: Start video job
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .basePath("/telemetry")
                .when()
                .post(String.format("/by-user/%d/%d/video/generate", joinedExamineeId, -10));

        // Assert
        assertThat(createResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(1204)
    void test_startBatchVideoGenerationWithInvalidExam_ok(){
        // Arrange

        // Act: Start video job
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .basePath("/telemetry")
                .when()
                .post(String.format("/by-exam/%d/video/generate-all", -110));

        // Assert
        assertThat(createResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.BAD_REQUEST);
    }

    @Test
    @Order(1205)
    void test_getAllVideoJobsOfExam_ok(){
        // Arrange

        // Act
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .basePath("/exams")
                .when()
                .get(String.format("/%d/videojobs", createdExam.id()));

        // Assert
        assertThat(createResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);
        VideoJobDto[] jobDtos = createResponse.then()
                .log().body()
                .extract().as(VideoJobDto[].class);
        assertThat(jobDtos)
                .hasSize(2);
    }

    @Test
    @Order(1206)
    void test_getAllVideoJobsOfInvalidExam_ok(){
        // Arrange

        // Act
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .basePath("/exams")
                .when()
                .get(String.format("/%d/videojobs", -1));

        // Assert
        assertThat(createResponse.statusCode())
                .isEqualTo(RestResponse.StatusCode.NOT_FOUND);
    }

    @Test
    @Order(1300)
    void test_simpleDeleteTelemetryOfExam_ok() {
        // Arrange

        // Act
        Response response = given()
                .basePath(BASE_URL)
            .when()
                .delete(String.format("%d/telemetry", createdExam.id()));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.NO_CONTENT);
    }

    @Test
    @Order(1400)
    void test_simpleDeleteExam_ok() {
        // Arrange

        // Act
        Response response = given()
                .basePath(BASE_URL)
            .when()
                .delete(Long.toString(createdExam.id()));

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.NO_CONTENT);
    }
}
