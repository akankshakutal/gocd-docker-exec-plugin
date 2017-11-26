package io.bitgrillr.goDockerBuildPlugin.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;

public class GoTestUtils {

  private static final String baseUrl = "http://localhost:8153/go/";
  private static final Executor executor;
  static {
    executor = Executor.newInstance().auth(new HttpHost("localhost", 8153), "admin", "changeme");
  }

  public static int runPipeline(String pipeline) throws GoError, IOException, InterruptedException {
    final Response scheduleResponse = executor.execute(
        Request.Post(baseUrl + "api/pipelines/" + pipeline + "/schedule").addHeader("Confirm", "true"));
    final int scheduleStatus = scheduleResponse.returnResponse().getStatusLine().getStatusCode();
    if (scheduleStatus != HttpStatus.SC_ACCEPTED) {
      throw new GoError(scheduleStatus);
    }

    Thread.sleep(5 * 1000);

    final HttpResponse historyResponse = executor.execute(
        Request.Get(baseUrl + "api/pipelines/" + pipeline + "/history")).returnResponse();
    final int historyStatus = historyResponse.getStatusLine().getStatusCode();
    if (historyStatus != HttpStatus.SC_OK) {
      throw new GoError(historyStatus);
    }
    final JsonArray pipelineInstances = Json.createReader(historyResponse.getEntity().getContent()).readObject()
        .getJsonArray("pipelines");
    JsonObject lastPipelineInstance = pipelineInstances.getJsonObject(0);
    for (JsonValue pipelineInstance : pipelineInstances) {
      if (pipelineInstance.asJsonObject().getInt("counter") > lastPipelineInstance.getInt("counter")) {
        lastPipelineInstance = pipelineInstance.asJsonObject();
      }
    }

    return lastPipelineInstance.getInt("counter");
  }

  public static String getPipelineResult(String pipeline, int counter) throws GoError, IOException {
    final HttpResponse response = executor.execute(
        Request.Get(baseUrl + "api/pipelines/" + pipeline + "/instance/" + Integer.toString(counter))).returnResponse();
    final int status = response.getStatusLine().getStatusCode();
    if (status != HttpStatus.SC_OK) {
      throw new GoError(status);
    }

    final JsonArray stages = Json.createReader(response.getEntity().getContent()).readObject().getJsonArray("stages");
    String result = "Passed";
    for (JsonValue stage : stages) {
      final String stageResult = ((JsonObject) stage).getString("result");
      if (!result.equals(stageResult)) {
        result = stageResult;
        break;
      }
    }
    return result;
  }

  public static void waitForPipeline(String pipeline, int counter) throws GoError, IOException, InterruptedException {
    boolean finished;
    do {
      Thread.sleep(5 * 1000);
      final HttpResponse response = executor.execute(
          Request.Get(baseUrl + "api/pipelines/" + pipeline + "/instance/" + Integer.toString(counter)))
          .returnResponse();
      final int status = response.getStatusLine().getStatusCode();
      if (status != HttpStatus.SC_OK) {
        throw new GoError(status);
      }
      final JsonArray stages = Json.createReader(response.getEntity().getContent()).readObject().getJsonArray("stages");
      finished = true;
      outerLoop: for (JsonValue stage : stages) {
        final JsonArray jobs = ((JsonObject) stage).getJsonArray("jobs");
        for (JsonValue job : jobs) {
          if (!"Completed".equals(((JsonObject) job).getString("state"))) {
            finished = false;
            break outerLoop;
          }
        }
      }
    } while (!finished);
  }

  public static String getPipelineLog(String pipeline, int counter, String stage, String job)
      throws GoError, IOException {
    final HttpResponse response = executor.execute(
        Request.Get(
            baseUrl + "files/" + pipeline + "/" + counter + "/" + stage + "/1/" + job + "/cruise-output/console.log"))
        .returnResponse();
    final int status = response.getStatusLine().getStatusCode();
    if (status != HttpStatus.SC_OK) {
      throw new GoError(status);
    }
    return new BufferedReader(new InputStreamReader(response.getEntity().getContent())).lines()
        .collect(Collectors.joining("\n")) + "\n";
  }

}
