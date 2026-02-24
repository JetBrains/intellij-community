// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.config;

import org.apache.commons.cli.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static org.jetbrains.idea.maven.config.MavenConfigSettings.CHECKSUM_FAILURE_POLICY;
import static org.jetbrains.idea.maven.config.MavenConfigSettings.CHECKSUM_WARNING_POLICY;
import static org.jetbrains.idea.maven.config.MavenConfigSettings.DEBUG;
import static org.jetbrains.idea.maven.config.MavenConfigSettings.FAIL_AT_END;
import static org.jetbrains.idea.maven.config.MavenConfigSettings.FAIL_FAST;
import static org.jetbrains.idea.maven.config.MavenConfigSettings.FAIL_NEVER;
import static org.jetbrains.idea.maven.config.MavenConfigSettings.QUIET;

public @Nullable class MavenConfig {
  private final Map<String, Option> optionMap;
  @NotNull private final Map<String, String> javaProperties;
  private final String baseDir;

  public MavenConfig(@NotNull Map<String, Option> map,
                     @NotNull Map<String, String> javaProperties,
                     @NotNull String dir) {
    this.optionMap = Objects.requireNonNull(map);
    this.javaProperties = javaProperties;
    this.baseDir = Objects.requireNonNull(dir);
  }

  public boolean hasOption(@NotNull MavenConfigSettings configSetting) {
    return optionMap.containsKey(configSetting.key);
  }

  public Map<String, String> getJavaProperties() {
    return javaProperties;
  }

  public String getOptionValue(@NotNull MavenConfigSettings configSetting) {
    Option option = optionMap.get(configSetting.key);
    return option == null ? null : option.getValue();
  }

  public MavenExecutionOptions.FailureMode getFailureMode() {
    if (hasOption(FAIL_NEVER)) return MavenExecutionOptions.FailureMode.NEVER;
    if (hasOption(FAIL_AT_END)) return MavenExecutionOptions.FailureMode.AT_END;
    if (hasOption(FAIL_FAST)) return MavenExecutionOptions.FailureMode.FAST;
    return null;
  }

  public MavenExecutionOptions.ChecksumPolicy getChecksumPolicy() {
    if (hasOption(CHECKSUM_WARNING_POLICY)) return MavenExecutionOptions.ChecksumPolicy.WARN;
    if (hasOption(CHECKSUM_FAILURE_POLICY)) return MavenExecutionOptions.ChecksumPolicy.FAIL;
    return null;
  }

  public MavenExecutionOptions.LoggingLevel getOutputLevel() {
    if (hasOption(QUIET)) return MavenExecutionOptions.LoggingLevel.DISABLED;
    if (hasOption(DEBUG)) return MavenExecutionOptions.LoggingLevel.DEBUG;
    return null;
  }

  public @Nullable String getFilePath(@NotNull MavenConfigSettings configSetting) {
    Option option = optionMap.get(configSetting.key);
    if (option == null) return null;

    Path file = Path.of(option.getValue());
    if (file.isAbsolute() && Files.exists(file)) return option.getValue();
    file = Path.of(baseDir, option.getValue());
    if (Files.exists(file)) return file.toAbsolutePath().toString();
    return null;
  }

  public Properties toProperties() {
    return new Properties();
  }

  public boolean isEmpty() {
    return optionMap.isEmpty();
  }
}
