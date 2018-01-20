package io.bitgrillr.gocddockerexecplugin;

import static org.hamcrest.CoreMatchers.containsString;
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
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.CoreMatchers;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Matcher;
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
    verifyPipeline("test", "Passed", Stream.<Matcher<Iterable<? super String>>>builder()
        .add(hasItem(containsString("BUILD SUCCESSFUL")))
        .build().collect(Collectors.toList()));

    List<String> console = new ArrayList<>();
    JobConsoleLogger logger = mock(JobConsoleLogger.class);
    doAnswer(i -> console.add(i.getArgument(0))).when(logger).printLine(anyString());
    PowerMockito.mockStatic(JobConsoleLogger.class);
    when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

    DockerUtils.execCommand("integrationtest_go-agent_1", null, "ls", "-l", "/go/pipelines/test/build/libs");
    assertThat("Created file ownership wrong", console, hasItem(matches(".*go go.+gocddockerexecplugin-.*\\.jar$")));
  }

  @Test
  public void noImage() throws Exception {
    verifyPipeline("testNoImage", "Failed", Stream.<Matcher<Iterable<? super String>>>builder()
        .add(hasItem(endsWith("Image 'idont:exist' not found")))
        .build().collect(Collectors.toList()));
  }

  @Test
  public void multiArg() throws Exception {
    verifyPipeline("testMultiArg", "Passed", Stream.<Matcher<Iterable<? super String>>>builder()
        .add(CoreMatchers.hasItem(endsWith("Hello World")))
        .build().collect(Collectors.toList()));
  }

  @Test
  public void noArg() throws Exception {
    verifyPipeline("testNoArg", "Passed", Collections.emptyList());
  }

  @Test
  public void envVars() throws Exception {
    verifyPipeline("testEnvVars", "Passed", Stream.<Matcher<Iterable<? super String>>>builder()
        .add(hasItem(matches(".*TEST1 = value1, TEST2 = value2, GO_PIPELINE_LABEL = \\d+$")))
        .build().collect(Collectors.toList()));
  }

  private static void verifyPipeline(String pipeline, String expectedResult,
      List<Matcher<Iterable<? super String>>> logAsserts) throws Exception {
    final int counter = GoTestUtils.runPipeline(pipeline);
    GoTestUtils.waitForPipeline(pipeline, counter);
    final String result = GoTestUtils.getPipelineResult(pipeline, counter);
    final List<String> log = GoTestUtils.getPipelineLog(pipeline, counter, "test", "test");

    assertEquals("Expected result wrong", expectedResult, result);
    for (Matcher<Iterable<? super String>> matcher : logAsserts) {
      assertThat(log, matcher);
    }
  }

  private static Matcher<String> matches(String pattern) {
    return new CustomTypeSafeMatcher<String>((new StringBuilder()).append("a String matching '").append(pattern)
        .append("'").toString()) {
      @Override
      protected boolean matchesSafely(String item) {
        return Pattern.compile(pattern).matcher(item).matches();
      }
    };
  }

}
