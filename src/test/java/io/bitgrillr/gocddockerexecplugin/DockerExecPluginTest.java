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
package io.bitgrillr.gocddockerexecplugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonObject;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JobConsoleLogger.class, DockerUtils.class, SystemHelper.class, IOUtils.class})
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
  public void handleViewError() throws Exception {
    PowerMockito.mockStatic(IOUtils.class);
    when(IOUtils.toString(any(InputStream.class), any(Charset.class))).thenThrow(new IOException("TESTERROR"));

    GoPluginApiResponse response = new DockerExecPlugin().handle(
        new DefaultGoPluginApiRequest(null, null, "view"));

    assertEquals("Expect 5xx response", DefaultGoPluginApiResponse.INTERNAL_ERROR, response.responseCode());
    assertEquals("Wrong body", "TESTERROR",
        Json.createReader(new StringReader(response.responseBody())).readObject().getString("exception"));
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
    UnitTestUtils.mockJobConsoleLogger();
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString(), anyString(), anyMap())).thenReturn("123");
    when(DockerUtils.getContainerUid(anyString())).thenReturn("4:5");
    when(DockerUtils.execCommand(anyString(), any(), any())).thenReturn(0);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());
    when(DockerUtils.getCommandString(anyString(), any())).thenCallRealMethod();
    PowerMockito.mockStatic(SystemHelper.class);
    when(SystemHelper.getSystemUid()).thenReturn("7:8");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
        .add("config", Json.createObjectBuilder()
            .add("IMAGE", Json.createObjectBuilder()
                .add("value", "ubuntu:latest")
                .build())
            .add("COMMAND", Json.createObjectBuilder()
                .add("value", "echo")
                .build())
            .add("ARGUMENTS", Json.createObjectBuilder()
                .add("value", "Hello\nWorld")
                .build())
            .build())
        .add("context", Json.createObjectBuilder()
            .add("workingDirectory", "pipelines/test")
            .add("environmentVariables", Json.createObjectBuilder()
                .add("TEST1", "value1")
                .add("TEST2", "value2")
                .build())
            .build())
        .build();
    request.setRequestBody(requestBody.toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.pullImage("ubuntu:latest");
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.createContainer("ubuntu:latest",
        Paths.get(System.getProperty("user.dir"), "pipelines/test").toAbsolutePath().toString(),
        Stream.<Map.Entry<String, String>>builder()
            .add(new SimpleEntry<>("TEST1", "value1"))
            .add(new SimpleEntry<>("TEST2", "value2"))
            .build().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.getContainerUid("123");
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.execCommand("123", "root", "chown", "-R", "4:5", ".");
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.execCommand("123", null, "echo", "Hello", "World");
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.execCommand("123", "root", "chown", "-R", "7:8", ".");
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.removeContainer("123");
    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected success", Boolean.TRUE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "Command 'echo 'Hello' 'World'' completed with status 0",
        Json.createReader(new StringReader(response.responseBody())).readObject().getString("message"));
  }

  @Test
  public void handleExecuteNoArgs() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString(), anyString(), anyMap())).thenReturn("123");
    when(DockerUtils.getContainerUid(anyString())).thenReturn("4:5");
    when(DockerUtils.execCommand(anyString(), any(), any())).thenReturn(0);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());
    PowerMockito.mockStatic(SystemHelper.class);
    when(SystemHelper.getSystemUid()).thenReturn("7:8");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
        .add("config", Json.createObjectBuilder()
            .add("IMAGE", Json.createObjectBuilder()
                .add("value", "ubuntu:latest")
                .build())
            .add("COMMAND", Json.createObjectBuilder()
                .add("value", "ls")
                .build())
            .add("ARGUMENTS", Json.createObjectBuilder()
                .build())
            .build())
        .add("context", Json.createObjectBuilder()
            .add("workingDirectory", "pipelines/test")
            .add("environmentVariables", Json.createObjectBuilder().build())
            .build())
        .build();
    request.setRequestBody(requestBody.toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.execCommand("123", null, "ls");
  }

  @Test
  public void handleExecuteFailure() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString(), anyString(), anyMap())).thenReturn("123");
    when(DockerUtils.getContainerUid(anyString())).thenReturn("4:5");
    when(DockerUtils.execCommand(anyString(), any(), anyString(), any())).thenAnswer(i -> {
      if (i.getArgument(2).equals("chown")) {
        return 0;
      } else {
        return 1;
      }
    });
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());
    PowerMockito.mockStatic(SystemHelper.class);
    when(SystemHelper.getSystemUid()).thenReturn("7:8");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
        .add("config", Json.createObjectBuilder()
            .add("IMAGE", Json.createObjectBuilder()
                .add("value", "ubuntu:latest")
                .build())
            .add("COMMAND", Json.createObjectBuilder()
                .add("value", "ls")
                .build())
            .add("ARGUMENTS", Json.createObjectBuilder()
                .build())
            .build())
        .add("context", Json.createObjectBuilder()
            .add("workingDirectory", "pipelines/test")
            .add("environmentVariables", Json.createObjectBuilder().build())
            .build())
        .build();
    request.setRequestBody(requestBody.toString());
    final GoPluginApiResponse response = new DockerExecPlugin().handle(request);

    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE, response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected failure", Boolean.FALSE, responseBody.getBoolean("success"));
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.execCommand("123", "root", "chown", "-R", "4:5", ".");
    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.execCommand("123", "root", "chown", "-R", "7:8", ".");
  }

  @Test
  public void handleExecuteImageNotFound() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    PowerMockito.mockStatic(DockerUtils.class);
    PowerMockito.doThrow(new ImageNotFoundException("idont:exist")).when(DockerUtils.class);
    DockerUtils.pullImage(anyString());

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
        .add("config", Json.createObjectBuilder()
            .add("IMAGE", Json.createObjectBuilder()
                .add("value", "idont:exist")
                .build())
            .add("COMMAND", Json.createObjectBuilder()
                .add("value", "ls")
                .build())
            .add("ARGUMENTS", Json.createObjectBuilder()
                .build())
            .build())
        .add("context", Json.createObjectBuilder()
            .add("workingDirectory", "pipelines/test")
            .add("environmentVariables", Json.createObjectBuilder().build())
            .build())
        .build();
    request.setRequestBody(requestBody.toString());
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
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString(), anyString(), anyMap())).thenReturn("123");
    when(DockerUtils.getContainerUid(anyString())).thenReturn("4:5");
    when(DockerUtils.execCommand(anyString(), any(), anyString(), any())).thenThrow(new DockerException("FAIL"));
    PowerMockito.doNothing().when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());
    PowerMockito.mockStatic(SystemHelper.class);
    when(SystemHelper.getSystemUid()).thenReturn("7:8");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
        .add("config", Json.createObjectBuilder()
            .add("IMAGE", Json.createObjectBuilder()
                .add("value", "ubuntu:latest")
                .build())
            .add("COMMAND", Json.createObjectBuilder()
                .add("value", "ls")
                .build())
            .add("ARGUMENTS", Json.createObjectBuilder()
                .build())
            .build())
        .add("context", Json.createObjectBuilder()
            .add("workingDirectory", "pipelines/test")
            .add("environmentVariables", Json.createObjectBuilder().build())
            .build())
        .build();
    request.setRequestBody(requestBody.toString());
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
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString(), anyString(), anyMap())).thenReturn("123");
    when(DockerUtils.getContainerUid(anyString())).thenReturn("4:5");
    when(DockerUtils.execCommand(anyString(), any(), anyString(), any())).thenReturn(0);
    PowerMockito.doThrow(new DockerException("FAIL")).when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());
    PowerMockito.mockStatic(SystemHelper.class);
    when(SystemHelper.getSystemUid()).thenReturn("7:8");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
        .add("config", Json.createObjectBuilder()
            .add("IMAGE", Json.createObjectBuilder()
                .add("value", "ubuntu:latest")
                .build())
            .add("COMMAND", Json.createObjectBuilder()
                .add("value", "ls")
                .build())
            .add("ARGUMENTS", Json.createObjectBuilder()
                .build())
            .build())
        .add("context", Json.createObjectBuilder()
            .add("workingDirectory", "pipelines/test")
            .add("environmentVariables", Json.createObjectBuilder().build())
            .build())
        .build();
    request.setRequestBody(requestBody.toString());
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
    DockerUtils.pullImage(anyString());
    when(DockerUtils.createContainer(anyString(), anyString(), anyMap())).thenReturn("123");
    when(DockerUtils.getContainerUid(anyString())).thenReturn("4:5");
    when(DockerUtils.execCommand(anyString(), any(), anyString(), any())).thenThrow(new DockerException("FAIL1"));
    PowerMockito.doThrow(new DockerException("FAIL2")).when(DockerUtils.class);
    DockerUtils.removeContainer(anyString());
    PowerMockito.mockStatic(SystemHelper.class);
    when(SystemHelper.getSystemUid()).thenReturn("7:8");

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
        .add("config", Json.createObjectBuilder()
            .add("IMAGE", Json.createObjectBuilder()
                .add("value", "ubuntu:latest")
                .build())
            .add("COMMAND", Json.createObjectBuilder()
                .add("value", "ls")
                .build())
            .add("ARGUMENTS", Json.createObjectBuilder()
                .build())
            .build())
        .add("context", Json.createObjectBuilder()
            .add("workingDirectory", "pipelines/test")
            .add("environmentVariables", Json.createObjectBuilder().build())
            .build())
        .build();
    request.setRequestBody(requestBody.toString());
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