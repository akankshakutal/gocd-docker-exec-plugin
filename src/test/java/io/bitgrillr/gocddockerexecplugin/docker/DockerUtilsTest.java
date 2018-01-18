package io.bitgrillr.gocddockerexecplugin.docker;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient.ExecCreateParam;
import com.spotify.docker.client.LogMessage;
import com.spotify.docker.client.LogMessage.Stream;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.exceptions.ImagePullFailedException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ExecCreation;
import com.spotify.docker.client.messages.ExecState;
import com.spotify.docker.client.messages.ProgressMessage;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import io.bitgrillr.gocddockerexecplugin.utils.UnitTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.hamcrest.CustomTypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JobConsoleLogger.class})
@PowerMockIgnore({"javax.net.ssl.*"})
public class DockerUtilsTest {

  @Test
  public void pullImage() throws Exception {
    final List<String> console = UnitTestUtils.mockJobConsoleLogger();

    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    doAnswer(i -> {
      ((ProgressHandler) i.getArgument(1)).progress(ProgressMessage.builder().status("Downloading").progress("DL1")
          .build());
      ((ProgressHandler) i.getArgument(1)).progress(ProgressMessage.builder().status("Extracting").progress("E1")
          .build());
      ((ProgressHandler) i.getArgument(1)).progress(ProgressMessage.builder().status("Image pulled").build());
      return null;
    }).when(dockerClient).pull(anyString(), any(ProgressHandler.class));
    DockerUtils.dockerClient = dockerClient;

    DockerUtils.pullImage("busybox:latest");

    assertEquals("Wrong number of lines", 4, console.size());
    assertEquals("Console log incorrect", "Pulling image 'busybox:latest'", console.get(0));
    assertEquals("Console log incorrect", "Downloading DL1", console.get(1));
    assertEquals("Console log incorrect", "Extracting E1", console.get(2));
    assertEquals("Console log incorrect", "Image pulled", console.get(3));
  }

  @Test(expected = ImageNotFoundException.class)
  public void pullBadImage() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    doAnswer(i -> {
      ((ProgressHandler) i.getArgument(1)).progress(ProgressMessage.builder().error("404 not found").build());
      return null;
    }).when(dockerClient).pull(anyString(), any(ProgressHandler.class));
    DockerUtils.dockerClient = dockerClient;

    DockerUtils.pullImage("bad:image");
  }

  @Test(expected = ImagePullFailedException.class)
  public void pullImageError() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    doAnswer(i -> {
      ((ProgressHandler) i.getArgument(1)).progress(ProgressMessage.builder().error("Server error").build());
      return null;
    }).when(dockerClient).pull(anyString(), any(ProgressHandler.class));
    DockerUtils.dockerClient = dockerClient;

    DockerUtils.pullImage("busybox:latest");
  }

  @Test
  public void createContainer() throws Exception {
    final List<String> console = UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    when(dockerClient.createContainer(any(ContainerConfig.class))).thenReturn(
        ContainerCreation.builder().id("123").build());
    doNothing().when(dockerClient).startContainer(anyString());
    DockerUtils.dockerClient = dockerClient;

    final String id = DockerUtils.createContainer("busybox:latest", "/some-dir");

    assertEquals("Wrong ID returned", "123", id);
    assertEquals("Wrong number of lines", 3, console.size());
    assertEquals("Console log incorrect", "Creating container from image 'busybox:latest'", console.get(0));
    assertEquals("Console log incorrect", "Created container '123'", console.get(1));
    assertEquals("Console log incorrect", "Started container '123'", console.get(2));
    ArgumentCaptor<ContainerConfig> containerConfig = ArgumentCaptor.forClass(ContainerConfig.class);
    verify(dockerClient).createContainer(containerConfig.capture());
    assertEquals("Image wrong", "busybox:latest", containerConfig.getValue().image());
    assertEquals("Working dir incorrect", "/app", containerConfig.getValue().workingDir());
    assertEquals("Bind mount not correct", "/some-dir:/app", containerConfig.getValue().hostConfig().binds().get(0));
  }

  @Test(expected = DockerException.class)
  public void createContainerError() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    when(dockerClient.createContainer(any(ContainerConfig.class))).thenThrow(new DockerException("FAIL"));
    DockerUtils.dockerClient = dockerClient;

    DockerUtils.createContainer("bad:image", "some-dir");
  }

  @Test
  public void execCommand() throws Exception {
    final List<String> console = UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    when(dockerClient.execCreate(anyString(), any(String[].class), any())).thenReturn(ExecCreation.create("456",
        Arrays.asList("warn1", "warn2")));
    final Iterator<String> messages = Arrays.asList("message1\n", "message2\n").iterator();
    final LogStream logStream = mock(LogStream.class);
    when(logStream.next()).thenAnswer(
        i -> new LogMessage(Stream.STDOUT, StandardCharsets.UTF_8.encode(messages.next())));
    when(logStream.hasNext()).thenAnswer(i -> messages.hasNext());
    doNothing().when(logStream).close();
    when(dockerClient.execStart(anyString(), any())).thenReturn(logStream);
    final ExecState execState = mock(ExecState.class);
    when(execState.exitCode()).thenReturn(0);
    when(dockerClient.execInspect(anyString())).thenReturn(execState);
    DockerUtils.dockerClient = dockerClient;

    final int exitCode = DockerUtils.execCommand("123", "user1", "echo", "true");

    ArgumentCaptor<ExecCreateParam> execCreateParams = ArgumentCaptor.forClass(ExecCreateParam.class);
    verify(dockerClient).execCreate(eq("123"), eq(new String[]{"echo", "true"}), execCreateParams.capture());
    assertThat("User not passed correctly", execCreateParams.getAllValues(), hasItem(ExecCreateParam.user("user1")));
    verify(dockerClient).execStart(eq("456"), any());
    verify(dockerClient).execInspect("456");
    assertEquals("Exit code non-zero", 0, exitCode);
    assertEquals("Wrong number of lines", 8, console.size());
    assertEquals("Console log incorrect", "Creating exec instance for command 'echo 'true''", console.get(0));
    assertEquals("Console log incorrect", "WARNING: warn1", console.get(1));
    assertEquals("Console log incorrect", "WARNING: warn2", console.get(2));
    assertEquals("Console log incorrect", "Created exec instance '456'", console.get(3));
    assertEquals("Console log incorrect", "Starting exec instance '456'", console.get(4));
    assertEquals("Console log incorrect", "message1", console.get(5));
    assertEquals("Console log incorrect", "message2", console.get(6));
    assertEquals("Console log incorrect", "Exec instance '456' exited with status 0", console.get(7));
  }

  @Test
  public void execCommandNoArgs() throws Exception {
    final List<String> console = UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    when(dockerClient.execCreate(anyString(), any(String[].class), any())).thenReturn(ExecCreation.create("456", null));
    final Iterator<String> messages = Arrays.asList("message1\n", "message2\n").iterator();
    final LogStream logStream = mock(LogStream.class);
    when(logStream.next()).thenAnswer(
        i -> new LogMessage(Stream.STDOUT, StandardCharsets.UTF_8.encode(messages.next())));
    when(logStream.hasNext()).thenAnswer(i -> messages.hasNext());
    doNothing().when(logStream).close();
    when(dockerClient.execStart(anyString(), any())).thenReturn(logStream);
    final ExecState execState = mock(ExecState.class);
    when(execState.exitCode()).thenReturn(0);
    when(dockerClient.execInspect(anyString())).thenReturn(execState);
    DockerUtils.dockerClient = dockerClient;

    DockerUtils.execCommand("123", "user1", "ls");

    verify(dockerClient).execCreate(eq("123"), eq(new String[]{"ls"}), any());
    assertThat("Console message incorrect", console, hasItem("Creating exec instance for command 'ls'"));
  }

  @Test
  public void execCommandNoUser() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    when(dockerClient.execCreate(anyString(), any(String[].class), any())).thenReturn(ExecCreation.create("456", null));
    final Iterator<String> messages = Arrays.asList("message1\n", "message2\n").iterator();
    final LogStream logStream = mock(LogStream.class);
    when(logStream.next()).thenAnswer(
        i -> new LogMessage(Stream.STDOUT, StandardCharsets.UTF_8.encode(messages.next())));
    when(logStream.hasNext()).thenAnswer(i -> messages.hasNext());
    doNothing().when(logStream).close();
    when(dockerClient.execStart(anyString(), any())).thenReturn(logStream);
    final ExecState execState = mock(ExecState.class);
    when(execState.exitCode()).thenReturn(0);
    when(dockerClient.execInspect(anyString())).thenReturn(execState);
    DockerUtils.dockerClient = dockerClient;

    DockerUtils.execCommand("123", null, "ls");

    ArgumentCaptor<ExecCreateParam> execCreateParams = ArgumentCaptor.forClass(ExecCreateParam.class);
    verify(dockerClient).execCreate(anyString(), any(String[].class), execCreateParams.capture());
    assertThat(execCreateParams.getAllValues(), not(hasItem(new CustomTypeSafeMatcher<ExecCreateParam>("a user param") {
      @Override
      protected boolean matchesSafely(ExecCreateParam item) {
        return (item.name().equals("User") && !item.value().isEmpty());
      }
    })));
  }

  @Test
  public void getContainerUid() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    when(dockerClient.execCreate(anyString(), any(String[].class), any())).thenReturn(ExecCreation.create("456", null));
    final Iterator<String> messages = Arrays.asList("1000:1000\n").iterator();
    final LogStream logStream = mock(LogStream.class);
    when(logStream.next()).thenAnswer(
        i -> new LogMessage(Stream.STDOUT, StandardCharsets.UTF_8.encode(messages.next())));
    when(logStream.hasNext()).thenAnswer(i -> messages.hasNext());
    doNothing().when(logStream).close();
    when(dockerClient.execStart(anyString(), any())).thenReturn(logStream);
    final ExecState execState = mock(ExecState.class);
    when(execState.exitCode()).thenReturn(0);
    when(dockerClient.execInspect(anyString())).thenReturn(execState);
    DockerUtils.dockerClient = dockerClient;

    final String uid = DockerUtils.getContainerUid("123");

    assertEquals("UID wrong", "1000:1000", uid);
  }

  @Test(expected = IllegalStateException.class)
  public void getContainerUidError() throws Exception {
    UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    when(dockerClient.execCreate(anyString(), any(String[].class), any())).thenReturn(ExecCreation.create("456", null));
    final Iterator<String> messages = Arrays.asList("1000").iterator();
    final LogStream logStream = mock(LogStream.class);
    when(logStream.next()).thenAnswer(
        i -> new LogMessage(Stream.STDOUT, StandardCharsets.UTF_8.encode(messages.next())));
    when(logStream.hasNext()).thenAnswer(i -> messages.hasNext());
    doNothing().when(logStream).close();
    when(dockerClient.execStart(anyString(), any())).thenReturn(logStream);
    final ExecState execState = mock(ExecState.class);
    when(execState.exitCode()).thenReturn(1);
    when(dockerClient.execInspect(anyString())).thenReturn(execState);
    DockerUtils.dockerClient = dockerClient;

    final String uid = DockerUtils.getContainerUid("123");
  }

  @Test
  public void removeContainer() throws Exception {
    final List<String> console = UnitTestUtils.mockJobConsoleLogger();
    final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
    doNothing().when(dockerClient).removeContainer(anyString(), any());
    DockerUtils.dockerClient = dockerClient;

    DockerUtils.removeContainer("123");

    assertEquals("Wrong number of lines output", 2, console.size());
    assertEquals("Console log incorrect", "Stopping container '123'", console.get(0));
    assertEquals("Console log incorrect", "Removing container '123'", console.get(1));
  }

  @Test
  public void getCommandString() {
    final String commandString = DockerUtils.getCommandString("echo", "Hello", "World");
    assertEquals("'echo 'Hello' 'World''", commandString);
  }

  @Test
  public void getCommandStringNoArgs() {
    final String commandString = DockerUtils.getCommandString("ls");
    assertEquals("'ls'", commandString);
  }
}
