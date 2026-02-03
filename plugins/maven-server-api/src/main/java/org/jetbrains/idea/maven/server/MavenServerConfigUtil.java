// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class MavenServerConfigUtil {
  private static final Pattern PROPERTY_PATTERN = Pattern.compile("\"-D([\\S&&[^=]]+)(?:=([^\"]+))?\"|-D([\\S&&[^=]]+)(?:=(\\S+))?");

  public static Map<String, String> getMavenAndJvmConfigPropertiesForNestedProjectDir(File nestedProjectDir) {
    if (nestedProjectDir == null) {
      return Collections.emptyMap();
    }
    File baseDir = MavenServerUtil.findMavenBasedir(nestedProjectDir);

    return getMavenAndJvmConfigPropertiesForBaseDir(baseDir.toPath());
  }

  public static Map<String, String> getMavenAndJvmConfigPropertiesForBaseDir(Path baseDir) {
    Map<String, String> result = new HashMap<>();
    readConfigFiles(baseDir, result);
    return result.isEmpty() ? Collections.emptyMap() : result;
  }

  @VisibleForTesting
  public static void readConfigFiles(Path baseDir, Map<String, String> result) {
    readConfigFile(baseDir, ".mvn/jvm.config", result, "");
    readConfigFile(baseDir, ".mvn/maven.config", result, "true");
  }

  private static void readConfigFile(Path baseDir, String relativePath, Map<String, String> result, String valueIfMissing) {
    Path configFile = baseDir.resolve(relativePath);

    if (Files.isRegularFile(configFile)) {
      try {
        String text = String.join("\n", Files.readAllLines(configFile, StandardCharsets.UTF_8));
        Matcher matcher = PROPERTY_PATTERN.matcher(text);
        while (matcher.find()) {
          if (matcher.group(1) != null) {
            result.put(matcher.group(1), StringUtilRt.notNullize(matcher.group(2), valueIfMissing));
          }
          else {
            result.put(matcher.group(3), StringUtilRt.notNullize(matcher.group(4), valueIfMissing));
          }
        }
      }
      catch (IOException ignore) {
      }
    }
  }
}
