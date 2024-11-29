// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.config;

import com.intellij.util.ArrayUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * partial copy of {@link org.apache.maven.cli.CLIManager}
 */
public final class MavenConfigParser {
  private MavenConfigParser() {}

  @Nullable
  public static MavenConfig parse(@NotNull String baseDir) {
    Options options = new Options();
    for (MavenConfigSettings value : MavenConfigSettings.values()) {
      options.addOption(value.toOption());
    }

    Path configFile = Path.of(baseDir, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
    List<String> configArgs = new ArrayList<>();
    try {
      if (Files.exists(configFile) && !Files.isDirectory(configFile)) {
        for (String arg : Files.readString(configFile, Charset.defaultCharset()).split("\\s+")) {
          if (!arg.isEmpty()) {
            configArgs.add(arg);
          }
        }
        String[] cleanArgs = CleanArgument.cleanArgs(ArrayUtil.toStringArray(configArgs));
        CommandLineParser parser = new GnuParser();
        CommandLine parse = parser.parse(options, cleanArgs, true);
        return new MavenConfig(Arrays.stream(parse.getOptions()).collect(Collectors.toMap(o -> o.getOpt(), Function.identity())), baseDir);
      }
    }
    catch (Exception e) {
      MavenLog.LOG.error("error read maven config " + configFile.toAbsolutePath(), e);
    }
    return null;
  }


  /**
   * copied from {@link org.apache.maven.cli.CleanArgument}
   */
  public static final class CleanArgument {
    public static String[] cleanArgs(String[] args) {
      List<String> cleaned = new ArrayList<>();

      StringBuilder currentArg = null;

      for (String arg : args) {
        boolean addedToBuffer = false;

        if (arg.startsWith("\"")) {
          if (currentArg != null) {
            cleaned.add(currentArg.toString());
          }

          currentArg = new StringBuilder(arg.substring(1));
          addedToBuffer = true;
        }

        if (addedToBuffer && arg.endsWith("\"")) {
          currentArg.setLength(currentArg.length() - 1);

          cleaned.add(currentArg.toString());

          currentArg = null;
          continue;
        }

        if (!addedToBuffer) {
          if (currentArg != null) {
            currentArg.append(' ').append(arg);
          }
          else {
            cleaned.add(arg);
          }
        }
      }

      if (currentArg != null) {
        cleaned.add(currentArg.toString());
      }

      int cleanedSz = cleaned.size();

      String[] cleanArgs;

      if (cleanedSz == 0) {
        cleanArgs = args;
      }
      else {
        cleanArgs = ArrayUtil.toStringArray(cleaned);
      }
      return cleanArgs;
    }
  }
}
