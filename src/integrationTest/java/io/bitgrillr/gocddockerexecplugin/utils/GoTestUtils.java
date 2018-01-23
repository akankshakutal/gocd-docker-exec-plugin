/**
 * Copyright 2018 Christopher Arnold <cma.arnold@gmail.com> and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.bitgrillr.gocddockerexecplugin.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
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

/**
 * Contains various methods for interacting with a Go.cd server via it's REST API.
 */
public class GoTestUtils {

  private static final String BASE_URL = "http://localhost:8153/go/";
  private static final String PIPELINES = BASE_URL + "api/pipelines/";
  private static final String FILES = BASE_URL + "files/";
  private static final Executor executor;

  static {
    executor = Executor.newInstance().auth(new HttpHost("localhost", 8153), "admin", "changeme");
  }

  private GoTestUtils() {}

  /**
   * Schedules the named pipeline to run.
   *
   * @param pipeline Name of the Pipeline to run.
   * @return Counter of the pipeline instance scheduled.
   * @throws GoError If Go.CD returns a non 2XX response.
   * @throws IOException If a communication error occurs.
   * @throws InterruptedException If something has gone horribly wrong.
   */
  public static int runPipeline(String pipeline) throws GoError, IOException, InterruptedException {
    final Response scheduleResponse = executor.execute(Request.Post(PIPELINES + pipeline + "/schedule")
        .addHeader("Confirm", "true"));
    final int scheduleStatus = scheduleResponse.returnResponse().getStatusLine().getStatusCode();
    if (scheduleStatus != HttpStatus.SC_ACCEPTED) {
      throw new GoError(scheduleStatus);
    }

    Thread.sleep(5 * 1000);

    final HttpResponse historyResponse = executor.execute(Request.Get(PIPELINES + pipeline + "/history"))
        .returnResponse();
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

  /**
   * Returns the result field of the named pipeline instance.
   *
   * @param pipeline Name of the pipeline.
   * @param counter Counter of the pipeline instance.
   * @return The result field.
   * @throws GoError If Go.CD returns a non 2XX response.
   * @throws IOException If a communication error occurs.
   */
  public static String getPipelineResult(String pipeline, int counter) throws GoError, IOException {
    final HttpResponse response = executor.execute(
        Request.Get(PIPELINES + pipeline + "/instance/" + Integer.toString(counter))).returnResponse();
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

  /**
   * Returns the status of the named pipeline instance.
   *
   * @param pipeline Name of the pipeline.
   * @param counter Counter of the pipeline instance.
   * @return The status.
   * @throws GoError If Go.CD returns a non 2XX reponse.
   * @throws IOException If a communication error occurs.
   */
  public static String getPipelineStatus(String pipeline, int counter) throws GoError, IOException {
    final HttpResponse response = executor.execute(
        Request.Get(PIPELINES + pipeline + "/instance/" + Integer.toString(counter))).returnResponse();
    final int status = response.getStatusLine().getStatusCode();
    if (status != HttpStatus.SC_OK) {
      throw new GoError(status);
    }
    final JsonArray stages = Json.createReader(response.getEntity().getContent()).readObject().getJsonArray("stages");
    String pipelineStatus = "Completed";
    for (JsonValue stage : stages) {
      final JsonArray jobs = ((JsonObject) stage).getJsonArray("jobs");
      for (JsonValue job : jobs) {
        pipelineStatus = job.asJsonObject().getString("state");
        if (!"Completed".equals(pipelineStatus)) {
          return pipelineStatus;
        }
      }
    }
    return pipelineStatus;
  }

  /**
   * Waits until the status of the pipeline instance is "Completed".
   *
   * @param pipeline Name of the pipeline.
   * @param counter Counter of the pipeline instance.
   * @throws GoError If Go.CD returns a non 2XX response.
   * @throws IOException If a communication error occurs.
   * @throws InterruptedException If something has gone horribly wrong.
   */
  public static void waitForPipeline(String pipeline, int counter) throws GoError, IOException, InterruptedException {
    boolean finished;
    do {
      Thread.sleep(5 * 1000);
      finished = "Completed".equals(getPipelineStatus(pipeline, counter));
    } while (!finished);
  }

  /**
   * Get the console log as a List of Strings from the pipeline instance for the specifed stage and job.
   *
   * @param pipeline Name of the pipeline.
   * @param counter Counter of the pipeline instance.
   * @param stage Name of the stage.
   * @param job Name of the job.
   * @return The job console log.
   * @throws GoError If Go.CD returns a non 2XX response.
   * @throws IOException If a communication error occurs.
   */
  public static List<String> getPipelineLog(String pipeline, int counter, String stage, String job)
      throws GoError, IOException {
    final HttpResponse response = executor.execute(
        Request.Get(FILES + pipeline + "/" + counter + "/" + stage + "/1/" + job + "/cruise-output/console.log"))
        .returnResponse();
    final int status = response.getStatusLine().getStatusCode();
    if (status != HttpStatus.SC_OK) {
      throw new GoError(status);
    }
    return new BufferedReader(new InputStreamReader(response.getEntity().getContent())).lines()
        .collect(Collectors.toList());
  }
}
