package io.bitgrillr.gocddockerexecplugin;

import com.spotify.docker.client.exceptions.DockerException;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;


@Extension
public class DockerExecPlugin extends AbstractGoPlugin {

  private static final String SUCCESS = "success";
  private static final String MESSAGE = "message";
  private static final String IMAGE = "IMAGE";
  private static final String CONFIG = "config";
  private static final String VALUE = "value";
  private static final String DISPLAY_NAME = "display-name";
  private static final String DISPLAY_ORDER = "display-order";

  private static final String IMAGE_REGEX =
      "([a-zA-Z][\\w\\-]*(\\.[a-zA-Z][\\w-]*)*(:\\d+)?/)?(\\w[\\w_\\-.]*/)?\\w[\\w_\\-.]*(:\\w[\\w_\\-.]*)?";

  @Override
  public GoPluginApiResponse handle(GoPluginApiRequest requestMessage) throws UnhandledRequestTypeException {
    final JsonObject requestBody;
    if (requestMessage.requestBody() != null) {
      requestBody = Json.createReader(new StringReader(requestMessage.requestBody())).readObject();
    } else {
      requestBody = null;
    }
    switch (requestMessage.requestName()) {
      case "configuration":
        return handleConfigRequest();
      case "view":
        return handleViewRequest();
      case "validate":
        if (requestBody == null) {
          throw new IllegalArgumentException("Request body null");
        }
        return handleValidateRequest(requestBody);
      case "execute":
        if (requestBody == null) {
          throw new IllegalArgumentException("Request body null");
        }
        return handleExecuteRequest(requestBody);
      default:
        throw new UnhandledRequestTypeException(requestMessage.requestName());
    }
  }

  @Override
  public GoPluginIdentifier pluginIdentifier() {
    return new GoPluginIdentifier("task", Collections.singletonList("1.0"));
  }

  private GoPluginApiResponse handleExecuteRequest(JsonObject requestBody) {
    final String image = requestBody.getJsonObject(CONFIG).getJsonObject(IMAGE).getString(VALUE);
    final String command = requestBody.getJsonObject(CONFIG).getJsonObject("COMMAND").getString(VALUE);
    final JsonString argumentsJson = requestBody.getJsonObject(CONFIG).getJsonObject("ARGUMENTS")
        .getJsonString(VALUE);
    final String[] arguments;
    if (argumentsJson != null) {
      arguments = argumentsJson.getString().split("\\r?\\n");
    } else {
      arguments = new String[0];
    }
    final String workingDir = requestBody.getJsonObject("context").getString("workingDirectory");
    final String pwd = Paths.get(System.getProperty("user.dir"), workingDir).toAbsolutePath().toString();

    final Map<String, Object> responseBody = new HashMap<>();
    try {
      final int exitCode = executeBuild(image, pwd, command, arguments);

      responseBody.put(MESSAGE, (new StringBuilder()).append("Command ")
          .append(DockerUtils.getCommandString(command, arguments)).append(" completed with status ").append(exitCode)
          .toString());
      if (exitCode == 0) {
        responseBody.put(SUCCESS, Boolean.TRUE);
      } else {
        responseBody.put(SUCCESS, Boolean.FALSE);
      }
    } catch (DockerCleanupException dce) {
      responseBody.clear();
      responseBody.put(SUCCESS, Boolean.FALSE);
      if (dce.getNested() == null) {
        responseBody.put(MESSAGE, dce.getCause().getMessage());
      } else {
        responseBody.put(MESSAGE, dce.getNested().getMessage());
      }
    } catch (ImageNotFoundException infe) {
      responseBody.put(SUCCESS, Boolean.FALSE);
      responseBody.put(MESSAGE, (new StringBuilder()).append("Image '").append(image).append("' not found").toString());
    } catch (Exception e) {
      responseBody.clear();
      JobConsoleLogger.getConsoleLogger().printLine("Exception occurred while executing task");
      logException(e);
      responseBody.put(SUCCESS, Boolean.FALSE);
      responseBody.put(MESSAGE, e.getMessage());
    }

    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(responseBody).build().toString());
  }

  private GoPluginApiResponse handleValidateRequest(JsonObject requestBody) {
    final Map<String, Object> responseBody = new HashMap<>();
    final Map<String, String> errors = new HashMap<>();

    final String image = requestBody.getJsonObject(IMAGE).getString(VALUE);
    if (!imageValid(image)) {
      errors.put(IMAGE, (new StringBuilder()).append("'").append(image).append("' is not a valid image identifier")
          .toString());
    }

    responseBody.put("errors", errors);
    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(responseBody).build().toString());
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
    final Map<String, Object> image = new HashMap<>();
    image.put(DISPLAY_NAME, "Image");
    image.put(DISPLAY_ORDER, "0");
    body.put(IMAGE, image);
    final Map<String, Object> command = new HashMap<>();
    command.put(DISPLAY_NAME, "Command");
    command.put(DISPLAY_ORDER, "1");
    body.put("COMMAND", command);
    final Map<String, Object> arguments = new HashMap<>();
    arguments.put(DISPLAY_NAME, "Arguments");
    arguments.put(DISPLAY_ORDER, "2");
    arguments.put("required", false);
    body.put("ARGUMENTS", arguments);

    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(body).build().toString());
  }

  private int executeBuild(String image, String pwd, String command, String[] arguments)
      throws DockerException, InterruptedException, IOException, DockerCleanupException {
    String containerId = null;
    Exception nestedException = null;
    try {
      DockerUtils.pullImage(image);

      containerId = DockerUtils.createContainer(image, pwd);

      final String systemUid = SystemHelper.getSystemUid();
      final String containerUid = DockerUtils.getContainerUid(containerId);

      JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Executing chown to container UID '")
          .append(containerUid).append("'").toString());
      final int chownContainerExitCode = DockerUtils.execCommand(containerId, "root", "chown", "-R", containerUid,
          ".");
      if (chownContainerExitCode != 0) {
        throw new IllegalStateException("chown to container UID failed");
      }

      StringBuilder logLine = (new StringBuilder()).append("Executing command '").append(command);
      for (String argument : arguments) {
        logLine.append(" '");
        logLine.append(argument);
        logLine.append("'");
      }
      logLine.append("'");
      JobConsoleLogger.getConsoleLogger().printLine(logLine.toString());
      final int cmdExitCode = DockerUtils.execCommand(containerId, null, command, arguments);

      JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Executing chown back to system UID '")
          .append(systemUid).append("'").toString());
      final int chownSystemExitCode = DockerUtils.execCommand(containerId, "root", "chown", "-R", systemUid, ".");
      if (chownSystemExitCode != 0) {
        throw new IllegalStateException("chown to system UID failed");
      }

      return cmdExitCode;
    } catch (Exception e) {
      nestedException = e;
      throw e;
    } finally {
      if (containerId != null) {
        try {
          DockerUtils.removeContainer(containerId);
        } catch (Exception e) {
          JobConsoleLogger.getConsoleLogger().printLine("Exception occurred while removing container");
          logException(e);
          if (nestedException == null) {
            throw new DockerCleanupException(e);
          } else {
            throw new DockerCleanupException(e, nestedException);
          }
        }
      }
    }
  }

  boolean imageValid(String image) {
    return Pattern.compile(IMAGE_REGEX).matcher(image).matches();
  }

  private void logException(Exception e) {
    JobConsoleLogger.getConsoleLogger().printLine(e.getMessage());
    for (StackTraceElement ste : e.getStackTrace()) {
      JobConsoleLogger.getConsoleLogger().printLine("\t" + ste.toString());
    }
  }

  private class DockerCleanupException extends Exception {
    private Throwable nested;

    private DockerCleanupException(Throwable cause) {
      super(cause);
    }

    private DockerCleanupException(Throwable cause, Throwable nested) {
      super(cause);
      this.nested = nested;
    }

    Throwable getNested() {
      return nested;
    }
  }
}
