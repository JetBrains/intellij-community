// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
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

    return getMavenAndJvmConfigPropertiesForBaseDir(baseDir);
  }

  public static Map<String, String> getMavenAndJvmConfigPropertiesForBaseDir(File baseDir) {
    Map<String, String> result = new HashMap<>();
    readConfigFiles(baseDir, result);
    return result.isEmpty() ? Collections.emptyMap() : result;
  }

  @VisibleForTesting
  public static void readConfigFiles(File baseDir, Map<String, String> result) {
    readConfigFile(baseDir, File.separator + ".mvn" + File.separator + "jvm.config", result, "");
    readConfigFile(baseDir, File.separator + ".mvn" + File.separator + "maven.config", result, "true");
  }

  private static void readConfigFile(File baseDir, String relativePath, Map<String, String> result, String valueIfMissing) {
    File configFile = new File(baseDir, relativePath);

    if (configFile.exists() && configFile.isFile()) {
      try {
        String text = FileUtilRt.loadFile(configFile, "UTF-8");
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
