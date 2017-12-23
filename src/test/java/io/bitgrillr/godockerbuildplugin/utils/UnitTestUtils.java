package io.bitgrillr.godockerbuildplugin.utils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import java.util.ArrayList;
import java.util.List;
import org.powermock.api.mockito.PowerMockito;

public class UnitTestUtils {

  private UnitTestUtils() {}

  public static List<String> mockJobConsoleLogger() {
    List<String> console = new ArrayList<>();
    JobConsoleLogger jobConsoleLogger = mock(JobConsoleLogger.class);
    doAnswer(i -> {
      console.add(i.getArgument(0));
      return null;
    }).when(jobConsoleLogger).printLine(anyString());
    PowerMockito.mockStatic(JobConsoleLogger.class);
    when(JobConsoleLogger.getConsoleLogger()).thenReturn(jobConsoleLogger);
    return console;
  }

}
