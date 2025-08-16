// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.sh.ShBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static com.intellij.execution.configurations.PathEnvironmentVariableUtil.findInPath;
import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;

public final class ShSettings {
  public static final Supplier<String> I_DO_MIND_SUPPLIER = ShBundle.messagePointer("i.do.mind.path.placeholder");

  private static final String SHELLCHECK_PATH = "SHELLCHECK.PATH";
  private static final String SHELLCHECK_SKIPPED_VERSION = "SHELLCHECK.SKIPPED.VERSION";
  private static final String SHELLCHECK_UNIX_EXECUTABLE = "shellcheck";
  private static final String SHELLCHECK_WIN_EXECUTABLE = "shellcheck.exe";
  private static final String SHFMT_PATH = "SHFMT.PATH";
  private static final String SHFMT_SKIPPED_VERSION = "SHFMT.SKIPPED.VERSION";
  private static final String SHFMT_UNIX_EXECUTABLE = "shfmt";
  private static final String SHFMT_WIN_EXECUTABLE = "shfmt.exe";

  public static @NotNull String getShellcheckPath(@NotNull Project project) {
    final var eelDescriptor = getEelDescriptor(project);

    // This is part of the migration from application-level PropertiesComponent to project-level. This code may be removed after some time.
    if (eelDescriptor == LocalEelDescriptor.INSTANCE) {
      final var oldProperty = PropertiesComponent.getInstance().getValue(SHELLCHECK_PATH, "");

      if (!oldProperty.isBlank()) {
        PropertiesComponent.getInstance().setValue(SHELLCHECK_PATH, "");
        setShellcheckPath(project, oldProperty);
        return oldProperty;
      }
    }

    final var defaultExecutable = findInPath(SystemInfo.isWindows ? SHELLCHECK_WIN_EXECUTABLE : SHELLCHECK_UNIX_EXECUTABLE);
    final var defaultExecutablePath = defaultExecutable != null ? defaultExecutable.getAbsolutePath() : "";

    return PropertiesComponent.getInstance(project).getValue(SHELLCHECK_PATH, defaultExecutablePath);
  }

  public static void setShellcheckPath(@NotNull Project project, @Nullable String path) {
    if (StringUtil.isNotEmpty(path)) PropertiesComponent.getInstance(project).setValue(SHELLCHECK_PATH, path);
  }

  public static @NotNull String getShfmtPath(@NotNull Project project) {
    final var eelDescriptor = getEelDescriptor(project);

    // This is part of the migration from application-level PropertiesComponent to project-level. This code may be removed after some time
    if (eelDescriptor == LocalEelDescriptor.INSTANCE) {
      final var oldProperty = PropertiesComponent.getInstance().getValue(SHFMT_PATH, "");

      if (!oldProperty.isBlank()) {
        PropertiesComponent.getInstance().setValue(SHFMT_PATH, "");
        setShfmtPath(project, oldProperty);
        return oldProperty;
      }
    }

    final var defaultExecutable = findInPath(SystemInfo.isWindows ? SHFMT_WIN_EXECUTABLE : SHFMT_UNIX_EXECUTABLE);
    final var defaultExecutablePath = defaultExecutable != null ? defaultExecutable.getAbsolutePath() : "";

    return PropertiesComponent.getInstance(project).getValue(SHFMT_PATH, defaultExecutablePath);
  }

  public static void setShfmtPath(@NotNull Project project, @Nullable String path) {
    if (StringUtil.isNotEmpty(path)) PropertiesComponent.getInstance(project).setValue(SHFMT_PATH, path);
  }

  public static @NotNull String getSkippedShellcheckVersion() {
    return PropertiesComponent.getInstance().getValue(SHELLCHECK_SKIPPED_VERSION, "");
  }

  public static void setSkippedShellcheckVersion(@NotNull String version) {
    if (StringUtil.isNotEmpty(version)) PropertiesComponent.getInstance().setValue(SHELLCHECK_SKIPPED_VERSION, version);
  }

  public static @NotNull String getSkippedShfmtVersion() {
    return PropertiesComponent.getInstance().getValue(SHFMT_SKIPPED_VERSION, "");
  }

  public static void setSkippedShfmtVersion(@NotNull String version) {
    if (StringUtil.isNotEmpty(version)) PropertiesComponent.getInstance().setValue(SHFMT_SKIPPED_VERSION, version);
  }
}
