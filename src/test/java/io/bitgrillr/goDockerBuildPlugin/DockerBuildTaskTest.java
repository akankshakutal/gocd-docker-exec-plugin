package io.bitgrillr.goDockerBuildPlugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.DefaultGoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import javax.json.Json;
import javax.json.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JobConsoleLogger.class)
public class DockerBuildTaskTest {

  @Test
  public void pluginIdentifier() throws Exception {
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
  public void handleViewError() throws Exception {

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
    PowerMockito.mockStatic(JobConsoleLogger.class);
    StringBuffer console = new StringBuffer();
    JobConsoleLogger logger = mock(JobConsoleLogger.class);
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        console.append((String) invocation.getArgument(0));
        return null;
      }
    }).when(logger).printLine(anyString());
    when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

    GoPluginApiResponse response = new DockerBuildTask().handle(
        new DefaultGoPluginApiRequest(null, null, "execute"));

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    String[] lines = console.toString().split("\n");
    assertEquals("Expected 1 line", 1, lines.length);
    assertEquals("Hello world!", lines[0]);
  }

  @Test(expected = UnhandledRequestTypeException.class)
  public void handleBadRequest() throws Exception {
    new DockerBuildTask().handle(new DefaultGoPluginApiRequest(null, null, "badRequest"));
  }

}