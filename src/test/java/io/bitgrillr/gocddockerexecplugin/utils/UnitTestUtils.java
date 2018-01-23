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
package io.bitgrillr.gocddockerexecplugin.utils;

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
