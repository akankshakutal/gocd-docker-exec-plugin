package io.bitgrillr.gocddockerexecplugin;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.spotify.docker.client.exceptions.ContainerNotFoundException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import io.bitgrillr.gocddockerexecplugin.docker.DockerUtils;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JobConsoleLogger.class)
@PowerMockIgnore({"javax.net.ssl.*"})
public class DockerTest {

  @Test
  public void exec() throws Exception {
    final List<String> console = mockJobConsoleLogger();

    DockerUtils.pullImage("busybox:latest");
    final String containerId = DockerUtils.createContainer("busybox:latest");
    final int exitCode = DockerUtils.execCommand(containerId, "echo", "Hello world!");
    DockerUtils.removeContainer(containerId);

    assertThat(console,
        either(hasItem("Status: Image is up to date for busybox:latest"))
            .or(hasItem("Status: Downloaded newer image for busybox:latest")));
    assertThat(console, hasItem("Creating container from image 'busybox:latest'"));
    assertThat(console, hasItem("Hello world!"));
    assertEquals("Incorrect exit code", 0, exitCode);
    assertThat(console, hasItem(CoreMatchers.startsWith("Removing container")));
  }

  @Test(expected = ImageNotFoundException.class)
  public void badPull() throws Exception {
    mockJobConsoleLogger();
    // please, no-one create this image on the hub
    DockerUtils.pullImage("idont:exist");
  }

  @Test(expected = ImageNotFoundException.class)
  public void badCreate() throws Exception {
    mockJobConsoleLogger();
    // again - please, no-one create this image on the hub
    DockerUtils.createContainer("idont:exist");
  }

  @Test
  public void badCommand() throws Exception {
    final List<String> console = mockJobConsoleLogger();

    DockerUtils.pullImage("busybox:latest");
    final String containerId = DockerUtils.createContainer("busybox:latest");
    final int exitCode = DockerUtils.execCommand(containerId, "doesntexist");
    DockerUtils.removeContainer(containerId);

    assertNotEquals("Wrong exit code", 0, exitCode);
    assertThat(console, hasItem(containsString("executable file not found")));
  }

  @Test
  public void failedCommand() throws Exception {
    mockJobConsoleLogger();

    DockerUtils.pullImage("busybox:latest");
    final String containerId = DockerUtils.createContainer("busybox:latest");
    final int exitCode = DockerUtils.execCommand(containerId, "false");
    DockerUtils.removeContainer(containerId);

    assertEquals("Wrong exit code", 1, exitCode);
  }

  @Test(expected = ContainerNotFoundException.class)
  public void badRemove() throws Exception {
    mockJobConsoleLogger();

    DockerUtils.removeContainer("shouldnotexist");
  }

  private List<String> mockJobConsoleLogger() {
    final List<String> console = new ArrayList<>();
    final JobConsoleLogger jobConsoleLogger = mock(JobConsoleLogger.class);
    doAnswer(i -> {
      console.add(i.getArgument(0));
      return null;
    }).when(jobConsoleLogger).printLine(anyString());
    PowerMockito.mockStatic(JobConsoleLogger.class);
    when(JobConsoleLogger.getConsoleLogger()).thenReturn(jobConsoleLogger);

    return console;
  }

}
