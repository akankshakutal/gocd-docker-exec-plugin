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
import com.spotify.docker.client.messages.HostConfig;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
   * @param pwd Working directory to be bind mounted into the container.
   * @return ID of the created container.
   * @throws DockerException If an error occurs creating the container.
   * @throws InterruptedException If the process is interrupted.
   */
  public static String createContainer(String image, String pwd, Map<String, String> envVars) throws DockerException,
      InterruptedException {
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder())
        .append("Creating container from image '").append(image).append("'").toString());
    final String id  = getDockerClient().createContainer(ContainerConfig.builder().image(image)
        .cmd("tail", "-f", "/dev/null")
        .hostConfig(HostConfig.builder().appendBinds(
            (new StringBuilder()).append(pwd).append(":/app").toString()).build())
        .workingDir("/app")
        .env(envVars.entrySet().stream().<List<String>>reduce(
            new ArrayList<>(),
            (memo, value) -> {
              memo.add((new StringBuilder().append(value.getKey()).append("=").append(value.getValue())).toString());
              return memo;
            },
            (memo1, memo2) -> {
              memo1.addAll(memo2);
              return memo1;
            })).build()).id();
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Created container '").append(id)
        .append("'").toString());
    getDockerClient().startContainer(id);
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Started container '").append(id)
        .append("'").toString());
    return id;
  }

  /**
   * Executes the specified command in the given container as the specified user.
   *
   * @param containerId Id of the container to execute the command in.
   * @param user User to execute the command as. Pass 'null' to run as default user of the container.
   * @param cmd Command to execute.
   * @param args An Array of arguments.
   * @return Exit code of the command.
   * @throws DockerException If an error occurs executing the command.
   * @throws InterruptedException If teh process is interrupted.
   */
  public static int execCommand(String containerId, String user, String cmd, String... args)
      throws DockerException, InterruptedException {
    return execCommand(containerId, line -> JobConsoleLogger.getConsoleLogger().printLine(line), user, cmd, args);
  }

  /**
   * Returns the UID of the default user of the container.
   *
   * @param containerId Id of the container.
   * @return The UID of the default user.
   * @throws DockerException In an error occurs executing the command.
   * @throws InterruptedException If the process is interrupted.
   */
  public static String getContainerUid(String containerId) throws DockerException, InterruptedException {
    final List<String> uid = new ArrayList<>();
    final int exitCode = execCommand(containerId, uid::add, null, "sh", "-c", "echo \"$(id -u):$(id -g)\"");
    if (exitCode != 0) {
      throw new IllegalStateException("echo ${UID} command failed");
    }
    return uid.get(0);
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

  /**
   * Return a String representation of the given command and arguments, e.g. "'echo 'hello' 'world'".
   *
   * @param command Command.
   * @param arguments Arguments.
   * @return Printable String.
   */
  public static String getCommandString(String command, String... arguments) {
    return (new StringBuilder())
        .append("'")
        .append(command)
        .append(Arrays.stream(arguments).reduce(
            new StringBuilder(),
            (accumulator, value) -> accumulator.append(" '").append(value).append("'"),
            (partial1, partial2) -> partial1.append(partial2.toString())).toString())
        .append("'")
        .toString();
  }

  private static int execCommand(String containerId, Printer printer, String user, String cmd, String... args)
      throws DockerException, InterruptedException {
    JobConsoleLogger.getConsoleLogger().printLine((new StringBuilder()).append("Creating exec instance for command ")
        .append(getCommandString(cmd, args)).toString());
    String[] cmdArray = new String[args.length + 1];
    cmdArray[0] = cmd;
    System.arraycopy(args, 0, cmdArray, 1, args.length);
    // for whatever reason, unless all streams are attached, the exec barfs and no-one knows why
    // see https://github.com/spotify/docker-client/issues/513
    List<ExecCreateParam> execCreateParams = new ArrayList<>();
    execCreateParams.add(ExecCreateParam.attachStdout());
    execCreateParams.add(ExecCreateParam.attachStderr());
    execCreateParams.add(ExecCreateParam.attachStdin());
    if (user != null) {
      execCreateParams.add(ExecCreateParam.user(user));
    }
    final ExecCreation execCreation = getDockerClient().execCreate(containerId, cmdArray,
        execCreateParams.toArray(new ExecCreateParam[execCreateParams.size()]));
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
        final String logMessage = StringUtils.chomp(StandardCharsets.UTF_8.decode(logStream.next().content())
            .toString());
        for (String logLine : logMessage.split("\n")) {
          printer.print(logLine);
        }
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

  private interface Printer {
    void print(String line);
  }

}
