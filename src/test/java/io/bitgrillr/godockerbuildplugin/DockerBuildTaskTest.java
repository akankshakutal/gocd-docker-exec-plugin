package io.bitgrillr.godockerbuildplugin;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.DefaultGoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import io.bitgrillr.godockerbuildplugin.docker.DockerUtils;
import io.bitgrillr.godockerbuildplugin.utils.UnitTestUtils;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import javax.json.Json;
import javax.json.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JobConsoleLogger.class, DockerUtils.class})
public class DockerBuildTaskTest {

  @Test
  public void pluginIdentifier() {
    GoPluginIdentifier identifier = new DockerBuildTask().pluginIdentifier();
    assertEquals("Wrong type", "task", identifier.getExtension());
    assertEquals("Wrong version", Collections.singletonList("1.0"), identifier.getSupportedExtensionVersions());
  }

  @Test
  public void handleConfiguration() throws Exception {
    GoPluginApiResponse response = new DockerBuildTask().handle(
        new DefaultGoPluginApiRequest(null, null, "configuration"));

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
  }

  @Test
  public void handleView() throws Exception {
    GoPluginApiResponse response = new DockerBuildTask().handle(
        new DefaultGoPluginApiRequest(null, null, "view"));

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    String expected = new String(Files.readAllBytes(Paths.get(
        getClass().getResource("/templates/task.template.html").toURI())));
    String actual = Json.createReader(new StringReader(response.responseBody())).readObject().getString("template");
    assertEquals("HTML content doesn't match", expected, actual);
  }

  @Test
  public void handleViewError() {
    // TODO: write this
  }

  @Test
  public void handleValidate() throws Exception {
    GoPluginApiResponse response = new DockerBuildTask().handle(
        new DefaultGoPluginApiRequest(null, null, "validate"));

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    JsonObject errors = Json.createReader(new StringReader(response.responseBody()))
        .readObject().getJsonObject("errors");
    assertEquals("Expected no errors", 0, errors.size());
  }

  @Test
  public void handleExecute() throws Exception {
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString())).thenReturn("123");
    when(DockerUtils.execCommand(anyString(), any())).thenReturn(0);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());

    final GoPluginApiResponse response = new DockerBuildTask().handle(
        new DefaultGoPluginApiRequest(null, null, "execute"));

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected success", Boolean.TRUE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "Command 'echo Hello World!' completed with status 0",
        Json.createReader(new StringReader(response.responseBody())).readObject().getString("message"));
  }

  @Test
  public void handleExecuteFailure() throws Exception {
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString())).thenReturn("123");
    when(DockerUtils.execCommand(anyString(), any())).thenReturn(1);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());

    final GoPluginApiResponse response = new DockerBuildTask()
        .handle(new DefaultGoPluginApiRequest(null, null, "execute"));

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
  }

  @Test
  public void handleExecuteImageNotFound() throws Exception {
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doThrow(new ImageNotFoundException("busybox:latest")).when(DockerUtils.class);
    DockerUtils.pullImage(anyString());

    final GoPluginApiResponse response = new DockerBuildTask().handle(
        new DefaultGoPluginApiRequest(null, null, "execute"));

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
    assertEquals("Message wrong", "Image 'busybox:latest' not found",responseBody.getString("message"));
  }

  @Test
  public void handleExecuteError() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString())).thenReturn("123");
    when(DockerUtils.execCommand(anyString(), any())).thenThrow(new DockerException("FAIL"));
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());

    final GoPluginApiResponse response = new DockerBuildTask()
        .handle(new DefaultGoPluginApiRequest(null, null, "execute"));

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "FAIL", responseBody.getString("message"));
  }

  @Test
  public void handleExecuteCleanupError() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString())).thenReturn("123");
    when(DockerUtils.execCommand(anyString(), any())).thenReturn(0);
    PowerMockito.doThrow(new DockerException("FAIL")).when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());

    final GoPluginApiResponse response = new DockerBuildTask()
        .handle(new DefaultGoPluginApiRequest(null, null, "execute"));

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "FAIL", responseBody.getString("message"));
  }

  @Test
  public void handleExecuteNestedCleanupError() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString())).thenReturn("123");
    when(DockerUtils.execCommand(anyString(), any())).thenThrow(new DockerException("FAIL1"));
    PowerMockito.doThrow(new DockerException("FAIL2")).when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());

    final GoPluginApiResponse response = new DockerBuildTask()
        .handle(new DefaultGoPluginApiRequest(null, null, "execute"));

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "FAIL1", responseBody.getString("message"));
  }

  @Test(expected = UnhandledRequestTypeException.class)
  public void handleBadRequest() throws Exception {
    new DockerBuildTask().handle(new DefaultGoPluginApiRequest(null, null, "badRequest"));
  }

}