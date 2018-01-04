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
