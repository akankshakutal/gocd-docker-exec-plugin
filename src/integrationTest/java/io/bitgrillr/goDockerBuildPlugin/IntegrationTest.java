package io.bitgrillr.goDockerBuildPlugin;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import io.bitgrillr.goDockerBuildPlugin.utils.GoTestUtils;
import org.junit.Test;

public class IntegrationTest {

  @Test
  public void build() throws Exception {
    final int counter = GoTestUtils.runPipeline("test");
    GoTestUtils.waitForPipeline("test", counter);
    final String result = GoTestUtils.getPipelineResult("test", counter);
    assertEquals("Expected success", "Passed", result);
    final String log = GoTestUtils.getPipelineLog("test", 1, "test", "test");
    assertThat("Missing message", log, containsString("Hello world!"));
  }

}
