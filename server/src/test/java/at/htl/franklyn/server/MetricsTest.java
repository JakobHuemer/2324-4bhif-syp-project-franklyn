package at.htl.franklyn.server;


import at.htl.franklyn.server.feature.exam.ExamInfoDto;
import at.htl.franklyn.server.feature.metrics.ServerMetricsDto;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@QuarkusTest
public class MetricsTest {
    private static final String BASE_URL = "metrics";

    @Test
    void test_simpleGetMetrics() {
        // Arrange

        // Act
        Response response = given()
                .basePath(BASE_URL)
                .when()
                .get();

        // Assert
        assertThat(response.statusCode())
                .isEqualTo(RestResponse.StatusCode.OK);

        ServerMetricsDto metricsDto = response.then()
                .log().body()
                .extract().as(ServerMetricsDto.class);

        assertThat(metricsDto)
                .isNotNull();

        assertThat(metricsDto.cpuUsagePercent())
                .isBetween(0.0, 100.0);
        assertThat(metricsDto.maxAvailableMemoryInBytes())
                .isGreaterThanOrEqualTo(0);
        assertThat(metricsDto.totalUsedMemoryInBytes())
                .isGreaterThanOrEqualTo(0);
        assertThat(metricsDto.maxAvailableMemoryInBytes())
                .isGreaterThanOrEqualTo(metricsDto.totalUsedMemoryInBytes());

        assertThat(metricsDto.remainingDiskSpaceInBytes())
                .isGreaterThanOrEqualTo(0);
        assertThat(metricsDto.savedScreenshotsSizeInBytes())
                .isGreaterThanOrEqualTo(0);
        assertThat(metricsDto.savedVideosSizeInBytes())
                .isGreaterThanOrEqualTo(0);
        assertThat(metricsDto.totalDiskSpaceInBytes())
                .isGreaterThanOrEqualTo(0);
        assertThat(metricsDto.totalDiskSpaceInBytes())
                .isGreaterThanOrEqualTo(
                        metricsDto.savedScreenshotsSizeInBytes()
                                + metricsDto.savedVideosSizeInBytes()
                                + metricsDto.remainingDiskSpaceInBytes()
                );
    }
}
