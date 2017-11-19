package io.bitgrillr.goDockerBuildPlugin;

import com.thoughtworks.go.plugin.api.AbstractGoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.json.Json;


@Extension
public class DockerBuildTask extends AbstractGoPlugin {

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
    JobConsoleLogger.getConsoleLogger().printLine("Hello world!");

    final Map<String, Object> body = new HashMap<>();
    body.put("success", Boolean.TRUE);
    body.put("message", "Task executed successfully");

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
        template = reader.lines().collect(Collectors.joining("\n"));
      }

      final Map<String, Object> body = new HashMap<>();
      body.put("displayValue", "Docker Build");
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
}
