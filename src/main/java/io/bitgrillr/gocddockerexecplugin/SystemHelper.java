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
package io.bitgrillr.gocddockerexecplugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

class SystemHelper {

  private SystemHelper() {}

  static String getSystemUid() throws IOException, InterruptedException {
    Process id = Runtime.getRuntime().exec(new String[] {"bash", "-c", "echo \"$(id -u):$(id -g)\""});
    id.waitFor();
    return StringUtils.chomp(IOUtils.toString(id.getInputStream(), StandardCharsets.UTF_8));
  }

}
