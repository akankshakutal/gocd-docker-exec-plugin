package io.bitgrillr.gocddockerexecplugin;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import io.bitgrillr.gocddockerexecplugin.utils.GoTestUtils;
import org.junit.Test;

public class IntegrationTest {

  @Test
  public void build() throws Exception {
    final PipelineResult result = PipelineResult.executePipeline("test");
    assertEquals("Expected success", "Passed", result.result);
    assertThat("Missing message", result.log, containsString("ID=ubuntu"));
  }

  @Test
  public void noImage() throws Exception {
    final PipelineResult result = PipelineResult.executePipeline("testNoImage");
    assertEquals("Expected failure", "Failed", result.result);
    assertThat("Missing message", result.log, containsString("Image 'idont:exist' not found"));
  }

  private static class PipelineResult {
    public final String result;
    public final String log;

    private PipelineResult(final String result, final String log) {
      this.result = result;
      this.log = log;
    }

    public static PipelineResult executePipeline(String pipeline) throws Exception {
      final int counter = GoTestUtils.runPipeline(pipeline);
      GoTestUtils.waitForPipeline(pipeline, counter);
      final String result = GoTestUtils.getPipelineResult(pipeline, counter);
      final String log = GoTestUtils.getPipelineLog(pipeline, counter, "test", "test");
      return new PipelineResult(result, log);
    }
  }

}
