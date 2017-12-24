package io.bitgrillr.gocddockerexecplugin;

import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.thoughtworks.go.plugin.api.AbstractGoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;

import io.bitgrillr.gocddockerexecplugin.docker.DockerUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.json.Json;


@Extension
public class DockerExecPlugin extends AbstractGoPlugin {

  public static final String SUCCESS = "success";
  public static final String MESSAGE = "message";

  @Override
  public GoPluginApiResponse handle(GoPluginApiRequest requestMessage) throws UnhandledRequestTypeException {
    switch (requestMessage.requestName()) {
      case "configuration":
        return handleConfigRequest();
      case "view":
        return handleViewRequest();
      case "validate":
        return handleValidateRequest();
      case "execute":
        return handleExecuteRequest();
      default:
        throw new UnhandledRequestTypeException(requestMessage.requestName());
    }
  }

  @Override
  public GoPluginIdentifier pluginIdentifier() {
    return new GoPluginIdentifier("task", Collections.singletonList("1.0"));
  }

  private GoPluginApiResponse handleExecuteRequest() {
    final Map<String, Object> body = new HashMap<>();
    String containerId = null;
    boolean nestedException = false;
    try {
      DockerUtils.pullImage("busybox:latest");

      containerId = DockerUtils.createContainer("busybox:latest");

      final int exitCode = DockerUtils.execCommand(containerId, "echo", "Hello World!");

      body.put(MESSAGE, (new StringBuilder()).append("Command '").append("echo Hello World!")
          .append("' completed with status ").append(exitCode).toString());
      if (exitCode == 0) {
        body.put(SUCCESS, Boolean.TRUE);
      } else {
        body.put(SUCCESS, Boolean.FALSE);
      }
    } catch (ImageNotFoundException infe) {
      nestedException = true;
      body.put(SUCCESS, Boolean.FALSE);
      body.put(MESSAGE, (new StringBuilder()).append("Image '").append("busybox:latest").append("' not found")
          .toString());
    } catch (Exception e) {
      nestedException = true;
      body.clear();
      JobConsoleLogger.getConsoleLogger().printLine("Exception occurred while executing task");
      printException(e);
      body.put(SUCCESS, Boolean.FALSE);
      body.put(MESSAGE, e.getMessage());
    } finally {
      if (containerId != null) {
        try {
          DockerUtils.removeContainer(containerId);
        } catch (Exception e) {
          JobConsoleLogger.getConsoleLogger().printLine("Exception occurred while removing container");
          printException(e);
          body.put(SUCCESS, Boolean.FALSE);
          if (!nestedException) {
            body.put(MESSAGE, e.getMessage());
          }
        }
      }
    }

    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(body).build().toString());
  }

  private GoPluginApiResponse handleValidateRequest() {
    final Map<String, Object> body = new HashMap<>();
    body.put("errors", new HashMap<String, Object>());

    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(body).build().toString());
  }

  private GoPluginApiResponse handleViewRequest() {
    try {
      final String template;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(
          getClass().getResourceAsStream("/templates/task.template.html"), StandardCharsets.UTF_8))) {
        template = reader.lines().collect(Collectors.joining("\n")) + "\n";
      }

      final Map<String, Object> body = new HashMap<>();
      body.put("displayValue", "Docker Exec");
      body.put("template", template);

      return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(body).build().toString());
    } catch (Exception e) {
      e.printStackTrace();
      final String body = Json.createObjectBuilder().add("exception", e.getMessage()).build().toString();
      return DefaultGoPluginApiResponse.error(body);
    }
  }

  private GoPluginApiResponse handleConfigRequest() {
    final Map<String, Object> body = new HashMap<>();
    final Map<String, Object> placeholder = new HashMap<>();
    placeholder.put("required", Boolean.FALSE);
    body.put("PLACEHOLDER", placeholder);

    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(body).build().toString());
  }

  private void printException(Exception e) {
    JobConsoleLogger.getConsoleLogger().printLine(e.getMessage());
    for (StackTraceElement ste : e.getStackTrace()) {
      JobConsoleLogger.getConsoleLogger().printLine("\t" + ste.toString());
    }
  }
}
