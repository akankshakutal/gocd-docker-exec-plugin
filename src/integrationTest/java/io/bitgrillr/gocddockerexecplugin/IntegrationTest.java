package io.bitgrillr.gocddockerexecplugin;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import io.bitgrillr.gocddockerexecplugin.docker.DockerUtils;
import io.bitgrillr.gocddockerexecplugin.utils.GoTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.hamcrest.CustomTypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JobConsoleLogger.class)
@PowerMockIgnore({"javax.net.ssl.*"})
public class IntegrationTest {

  @Test
  public void build() throws Exception {
    final PipelineResult result = PipelineResult.executePipeline("test");
    assertEquals("Expected success", "Passed", result.result);
    assertThat("Missing message", result.log, hasItem(endsWith("build.gradle")));
    assertThat("test file wrong in container", result.log, hasItem(new CustomTypeSafeMatcher<String>("matches") {
      @Override
      protected boolean matchesSafely(String item) {
        return Pattern.compile(".*root root.+test$").matcher(item).matches();
      }
    }));

    List<String> console = new ArrayList<>();
    JobConsoleLogger logger = mock(JobConsoleLogger.class);
    doAnswer(i -> console.add(i.getArgument(0))).when(logger).printLine(anyString());
    PowerMockito.mockStatic(JobConsoleLogger.class);
    when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

    DockerUtils.execCommand("integrationtest_go-agent_1", null, "ls", "-l", "/go/pipelines/test");
    assertThat("test file wrong on host", console, hasItem(new CustomTypeSafeMatcher<String>("matches") {
      @Override
      protected boolean matchesSafely(String item) {
        return Pattern.compile(".*go go.+test$").matcher(item).matches();
      }
    }));
  }

  @Test
  public void noImage() throws Exception {
    final PipelineResult result = PipelineResult.executePipeline("testNoImage");
    assertEquals("Expected failure", "Failed", result.result);
    assertThat("Missing message", result.log, hasItem(endsWith("Image 'idont:exist' not found")));
  }

  private static class PipelineResult {
    public final String result;
    public final List<String> log;

    private PipelineResult(final String result, final List<String> log) {
      this.result = result;
      this.log = log;
    }

    public static PipelineResult executePipeline(String pipeline) throws Exception {
      final int counter = GoTestUtils.runPipeline(pipeline);
      GoTestUtils.waitForPipeline(pipeline, counter);
      final String result = GoTestUtils.getPipelineResult(pipeline, counter);
      final List<String> log = GoTestUtils.getPipelineLog(pipeline, counter, "test", "test");
      return new PipelineResult(result, log);
    }
  }

}
