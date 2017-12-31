package io.bitgrillr.gocddockerexecplugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.DefaultGoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import io.bitgrillr.gocddockerexecplugin.docker.DockerUtils;
import io.bitgrillr.gocddockerexecplugin.utils.UnitTestUtils;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JobConsoleLogger.class, DockerUtils.class})
public class DockerExecPluginTest {

  @Test
  public void pluginIdentifier() {
    GoPluginIdentifier identifier = new DockerExecPlugin().pluginIdentifier();
    assertEquals("Wrong type", "task", identifier.getExtension());
    assertEquals("Wrong version", Collections.singletonList("1.0"), identifier.getSupportedExtensionVersions());
  }

  @Test
  public void handleConfiguration() throws Exception {
    GoPluginApiResponse response = new DockerExecPlugin().handle(
        new DefaultGoPluginApiRequest(null, null, "configuration"));

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
  }

  @Test
  public void handleView() throws Exception {
    GoPluginApiResponse response = new DockerExecPlugin().handle(
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
    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "validate");
    Map<String, Object> body = new HashMap<>();
    Map<String, String> image = new HashMap<>();
    image.put("value", "ubuntu:latest");
    body.put("IMAGE", image);
    request.setRequestBody(Json.createObjectBuilder(body).build().toString());

    GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    JsonObject errors = Json.createReader(new StringReader(response.responseBody()))
        .readObject().getJsonObject("errors");
    assertEquals("Expected no errors", 0, errors.size());
  }

  @Test
  public void handleValidateError() throws Exception {
    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "validate");
    Map<String, Object> body = new HashMap<>();
    Map<String, String> image = new HashMap<>();
    image.put("value", "ubuntu:");
    body.put("IMAGE", image);
    request.setRequestBody(Json.createObjectBuilder(body).build().toString());

    GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    JsonObject errors = Json.createReader(new StringReader(response.responseBody()))
        .readObject().getJsonObject("errors");
    assertEquals("Expected 1 error", 1, errors.size());
    assertEquals("Wrong message", "'ubuntu:' is not a valid image identifier", errors.getString("IMAGE"));
  }

  @Test
  public void handleExecute() throws Exception {
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage("ubuntu:latest");
    when(DockerUtils.createContainer("ubuntu:latest")).thenReturn("123");
    when(DockerUtils.execCommand(eq("123"), any())).thenReturn(0);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer("123");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    Map<String, Object> requestBody = new HashMap<>();
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> image = new HashMap<>();
    image.put("value", "ubuntu:latest");
    config.put("IMAGE", image);
    requestBody.put("config", config);
    request.setRequestBody(Json.createObjectBuilder(requestBody).build().toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected success", Boolean.TRUE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "Command 'cat /etc/os-release' completed with status 0",
        Json.createReader(new StringReader(response.responseBody())).readObject().getString("message"));
  }

  @Test
  public void handleExecuteFailure() throws Exception {
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage("ubuntu:latest");
    when(DockerUtils.createContainer("ubuntu:latest")).thenReturn("123");
    when(DockerUtils.execCommand(eq("123"), any())).thenReturn(1);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer("123");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    Map<String, Object> requestBody = new HashMap<>();
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> image = new HashMap<>();
    image.put("value", "ubuntu:latest");
    config.put("IMAGE", image);
    requestBody.put("config", config);
    request.setRequestBody(Json.createObjectBuilder(requestBody).build().toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
  }

  @Test
  public void handleExecuteImageNotFound() throws Exception {
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doThrow(new ImageNotFoundException("idont:exist")).when(DockerUtils.class);
    DockerUtils.pullImage("idont:exist");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    Map<String, Object> requestBody = new HashMap<>();
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> image = new HashMap<>();
    image.put("value", "idont:exist");
    config.put("IMAGE", image);
    requestBody.put("config", config);
    request.setRequestBody(Json.createObjectBuilder(requestBody).build().toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
    assertEquals("Message wrong", "Image 'idont:exist' not found",responseBody.getString("message"));
  }

  @Test
  public void handleExecuteError() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage("ubuntu:latest");
    when(DockerUtils.createContainer("ubuntu:latest")).thenReturn("123");
    when(DockerUtils.execCommand(eq("123"), any())).thenThrow(new DockerException("FAIL"));
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer("123");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    Map<String, Object> requestBody = new HashMap<>();
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> image = new HashMap<>();
    image.put("value", "ubuntu:latest");
    config.put("IMAGE", image);
    requestBody.put("config", config);
    request.setRequestBody(Json.createObjectBuilder(requestBody).build().toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

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
    DockerUtils.pullImage("ubuntu:latest");
    when(DockerUtils.createContainer("ubuntu:latest")).thenReturn("123");
    when(DockerUtils.execCommand(eq("123"), any())).thenReturn(0);
    PowerMockito.doThrow(new DockerException("FAIL")).when(DockerUtils.class);
    DockerUtils.removeContainer("123");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    Map<String, Object> requestBody = new HashMap<>();
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> image = new HashMap<>();
    image.put("value", "ubuntu:latest");
    config.put("IMAGE", image);
    requestBody.put("config", config);
    request.setRequestBody(Json.createObjectBuilder(requestBody).build().toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

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
    DockerUtils.pullImage("ubuntu:latest");
    when(DockerUtils.createContainer("ubuntu:latest")).thenReturn("123");
    when(DockerUtils.execCommand(eq("123"), any())).thenThrow(new DockerException("FAIL1"));
    PowerMockito.doThrow(new DockerException("FAIL2")).when(DockerUtils.class);
    DockerUtils.removeContainer("123");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    Map<String, Object> requestBody = new HashMap<>();
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> image = new HashMap<>();
    image.put("value", "ubuntu:latest");
    config.put("IMAGE", image);
    requestBody.put("config", config);
    request.setRequestBody(Json.createObjectBuilder(requestBody).build().toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "FAIL1", responseBody.getString("message"));
  }

  @Test(expected = UnhandledRequestTypeException.class)
  public void handleBadRequest() throws Exception {
    new DockerExecPlugin().handle(new DefaultGoPluginApiRequest(null, null, "badRequest"));
  }

  @Test
  public void imageValid() {
    final DockerExecPlugin dockerExecPlugin = new DockerExecPlugin();
    assertTrue(dockerExecPlugin.imageValid("t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("t-e_s.t/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("t-e_s.t/t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server/t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server/t-e_s.t/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server/t-e_s.t/t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server.my-domain/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server.my-domain/t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server.my-domain/t-e_s.t/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server.my-domain/t-e_s.t/t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server:8080/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server:8080/t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server:8080/t-e_s.t/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server:8080/t-e_s.t/t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server.my-domain:8080/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server.my-domain:8080/t-e_s.t:lat-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server.my-domain:8080/t-e_s.t/t-e_s.t"));
    assertTrue(dockerExecPlugin.imageValid("my-server.my-domain:8080/t-e_s.t/t-e_s.t:lat-e_s.t"));

    assertFalse(dockerExecPlugin.imageValid(""));
    assertFalse(dockerExecPlugin.imageValid("  "));
    assertFalse(dockerExecPlugin.imageValid("te%sd"));
    assertFalse(dockerExecPlugin.imageValid("-test"));
    assertFalse(dockerExecPlugin.imageValid("test:"));
    assertFalse(dockerExecPlugin.imageValid("test:-latest"));
    assertFalse(dockerExecPlugin.imageValid("te%st/test"));
    assertFalse(dockerExecPlugin.imageValid("-test/test"));
    assertFalse(dockerExecPlugin.imageValid("test/test:"));
    assertFalse(dockerExecPlugin.imageValid("test/test:-latest"));
    assertFalse(dockerExecPlugin.imageValid("my$server/test/test"));
    assertFalse(dockerExecPlugin.imageValid("-myserver/test/test"));
    assertFalse(dockerExecPlugin.imageValid("my-server:/test/test"));
    assertFalse(dockerExecPlugin.imageValid("my-server:abc/test/test"));
    assertFalse(dockerExecPlugin.imageValid("my-server./test/test"));
    assertFalse(dockerExecPlugin.imageValid("my-server.my$domain/test/test"));
    assertFalse(dockerExecPlugin.imageValid("my-server.-mydomain/test/test"));
    assertFalse(dockerExecPlugin.imageValid("my-server.my-domain:/test/test"));
    assertFalse(dockerExecPlugin.imageValid("my-server.my-domain:abc/test/test"));
  }
}