package io.bitgrillr.gocddockerexecplugin.docker;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ExecCreateParam;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.exceptions.ImagePullFailedException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ExecCreation;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang.StringUtils;

/**
 * Contains various utility methods for interacting with the Docker daemon.
 */
public class DockerUtils {

  static DockerClient dockerClient = null;

  private DockerUtils() {}

  static DockerClient getDockerClient() {
    if (dockerClient == null) {
      dockerClient = new DefaultDockerClient(System.getProperty("gocddockerexecplugin.dockerhost",
          "unix:///var/run/docker.sock"));
    }
    return dockerClient;
  }

  /**
   * Pulls the specified image.
   *
   * @param image Image to pull.
   * @throws DockerException If an error occurs.
   * @throws InterruptedException If the process is interrupted.
   */
  public static void pullImage(String image) throws DockerException, InterruptedException {
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Pulling image '").append(image)
        .append("'").toString());
    // basic logic for ProgressHandler pulled from LoggingPullHandler in docker-client
    getDockerClient().pull(image, pm -> {
      if (pm.error() != null) {
        if (pm.error().contains("404") || pm.error().toLowerCase().contains("not found")) {
          throw new ImageNotFoundException(image, pm.toString());
        } else {
          throw new ImagePullFailedException(image, pm.toString());
        }
      } else {
        StringBuilder message = new StringBuilder().append(pm.status());
        if ("Downloading".equals(pm.status()) || "Extracting".equals(pm.status())) {
          message.append(" ");
          message.append(pm.progress());
        }
        JobConsoleLogger.getConsoleLogger().printLine(message.toString());
      }
    });
  }

  /**
   * Creates a container for the specified image and starts it with the command 'tail -f /dev/null'.
   *
   * @param image Image to create the container from.
   * @return ID of the created container.
   * @throws DockerException If an error occurs creating the container.
   * @throws InterruptedException If the process is interrupted.
   */
  public static String createContainer(String image) throws DockerException, InterruptedException {
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder())
        .append("Creating container from image '").append(image).append("'").toString());
    final String id  = getDockerClient().createContainer(ContainerConfig.builder().image(image)
        .cmd("tail", "-f", "/dev/null").build()).id();
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Created container '").append(id)
        .append("'").toString());
    getDockerClient().startContainer(id);
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Started container '").append(id)
        .append("'").toString());
    return id;
  }

  /**
   * Executes the specified command in the given container as the "root" user. root is used to ensure that any
   * commands have permissions to write to the build directory.
   *
   * @param containerId Id of the container to execute the command in.
   * @param cmd Command to execute as an Array of arguments.
   * @return Exit code of the command.
   * @throws DockerException If an error occurs executing the command.
   * @throws InterruptedException If teh process is interrupted.
   */
  public static int execCommand(String containerId, String... cmd) throws DockerException, InterruptedException {
    final StringBuilder execCreateMessage = (new StringBuilder()).append("Creating exec instance for command '");
    for (int i = 0; i < cmd.length; i++) {
      execCreateMessage.append(cmd[i]);
      if (i < cmd.length - 1) {
        execCreateMessage.append(" ");
      }
    }
    execCreateMessage.append("'");
    JobConsoleLogger.getConsoleLogger().printLine(execCreateMessage.toString());
    // for whatever reason, unless all streams are attached, the exec barfs and no-one knows why
    // see https://github.com/spotify/docker-client/issues/513
    final ExecCreation execCreation = getDockerClient().execCreate(containerId, cmd,
        ExecCreateParam.user("root"), ExecCreateParam.attachStdout(), ExecCreateParam.attachStderr(),
        ExecCreateParam.attachStdin());
    if (execCreation.warnings() != null && !execCreation.warnings().isEmpty()) {
      for (final String warning : execCreation.warnings()) {
        JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("WARNING: ").append(warning)
            .toString());
      }
    }
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Created exec instance '")
        .append(execCreation.id()).append("'").toString());

    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Starting exec instance '")
        .append(execCreation.id()).append("'").toString());
    try (final LogStream logStream = getDockerClient().execStart(execCreation.id())) {
      while (logStream.hasNext()) {
        JobConsoleLogger.getConsoleLogger().printLine(
            StringUtils.chomp(StandardCharsets.UTF_8.decode(logStream.next().content()).toString()));
      }
    }

    final Integer exitStatus = getDockerClient().execInspect(execCreation.id()).exitCode();
    if (exitStatus == null) {
      throw new IllegalStateException("Exit code of exec comand null");
    }
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Exec instance '")
        .append(execCreation.id()).append("' exited with status ").append(exitStatus).toString());
    return exitStatus;
  }

  /**
   * Stops and removes the specified container and it's volumes ('docker rm -v containerId'). This will
   * wait one minute before issuing SIGKILL to the container.
   *
   * @param containerId ID of container to remove.
   * @throws DockerException If an occurs removing the container.
   * @throws InterruptedException If the process is interrupted.
   */
  public static void removeContainer(String containerId) throws DockerException, InterruptedException {
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Stopping container '")
        .append(containerId).append("'").toString());
    getDockerClient().stopContainer(containerId, 60);

    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Removing container '")
        .append(containerId).append("'").toString());
    getDockerClient().removeContainer(containerId, RemoveContainerParam.removeVolumes());
  }

}
