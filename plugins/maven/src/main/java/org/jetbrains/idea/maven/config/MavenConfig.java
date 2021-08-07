// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.config;

import org.apache.commons.cli.Option;
import org.apache.commons.lang.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;

import java.io.File;
import java.util.Map;
import java.util.Objects;

import static org.jetbrains.idea.maven.config.MavenConfigSettings.*;

@Nullable
public class MavenConfig {
  private final Map<String, Option> optionMap;
  private final String baseDir;

  public MavenConfig(@NotNull Map<String, Option> map, @NotNull String dir) {
    optionMap = Objects.requireNonNull(map);
    baseDir = Objects.requireNonNull(dir);
  }

  public Boolean hasOption(@NotNull MavenConfigSettings configSetting) {
    return optionMap.containsKey(configSetting.key);
  }

  public String getOptionValue(@NotNull MavenConfigSettings configSetting) {
    Option option = optionMap.get(configSetting.key);
    return option == null ? null : option.getValue();
  }

  public MavenExecutionOptions.FailureMode getFailureMode() {
    Boolean failure = hasOption(FAIL_NEVER);
    if (BooleanUtils.isTrue(failure)) return MavenExecutionOptions.FailureMode.NEVER;

    failure = hasOption(FAIL_AT_END);
    if (BooleanUtils.isTrue(failure)) return MavenExecutionOptions.FailureMode.AT_END;

    failure = hasOption(FAIL_FAST);
    if (BooleanUtils.isTrue(failure)) return MavenExecutionOptions.FailureMode.FAST;

    return null;
  }

  public MavenExecutionOptions.ChecksumPolicy getChecksumPolicy() {
    Boolean checkSum = hasOption(CHECKSUM_WARNING_POLICY);
    if (BooleanUtils.isTrue(checkSum)) return MavenExecutionOptions.ChecksumPolicy.WARN;

    checkSum = hasOption(CHECKSUM_FAILURE_POLICY);
    if (BooleanUtils.isTrue(checkSum)) return MavenExecutionOptions.ChecksumPolicy.FAIL;

    return null;
  }

  public MavenExecutionOptions.LoggingLevel getOutputLevel() {
    Boolean level = hasOption(QUIET);
    if (BooleanUtils.isTrue(level)) return MavenExecutionOptions.LoggingLevel.DISABLED;

    level = hasOption(DEBUG);
    if (BooleanUtils.isTrue(level)) return MavenExecutionOptions.LoggingLevel.DEBUG;

    return null;
  }

  public String getFilePath(@NotNull MavenConfigSettings configSetting) {
    Option option = optionMap.get(configSetting.key);
    if (option == null) return null;

    File file = new File(option.getValue());
    if (file.isAbsolute() && file.exists()) return option.getValue();
    file = new File(baseDir, option.getValue());
    if (file.exists()) return file.getAbsolutePath();
    return null;
  }

  public boolean isEmpty() {
    return optionMap.isEmpty();
  }
}
