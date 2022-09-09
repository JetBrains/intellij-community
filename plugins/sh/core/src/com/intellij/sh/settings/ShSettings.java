// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.sh.ShBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class ShSettings {
  public static final Supplier<String> I_DO_MIND_SUPPLIER = ShBundle.messagePointer("i.do.mind.path.placeholder");

  private static final String SHELLCHECK_PATH = "SHELLCHECK.PATH";
  private static final String SHELLCHECK_SKIPPED_VERSION = "SHELLCHECK.SKIPPED.VERSION";
  private static final String SHFMT_PATH = "SHFMT.PATH";
  private static final String SHFMT_SKIPPED_VERSION = "SHFMT.SKIPPED.VERSION";

  @NotNull
  public static String getShellcheckPath() {
    return PropertiesComponent.getInstance().getValue(SHELLCHECK_PATH, "");
  }

  public static void setShellcheckPath(@Nullable String path) {
    if (StringUtil.isNotEmpty(path)) PropertiesComponent.getInstance().setValue(SHELLCHECK_PATH, path);
  }

  @NotNull
  public static String getShfmtPath() {
    return PropertiesComponent.getInstance().getValue(SHFMT_PATH, "");
  }

  public static void setShfmtPath(@Nullable String path) {
    if (StringUtil.isNotEmpty(path)) PropertiesComponent.getInstance().setValue(SHFMT_PATH, path);
  }

  @NotNull
  public static String getSkippedShellcheckVersion() {
    return PropertiesComponent.getInstance().getValue(SHELLCHECK_SKIPPED_VERSION, "");
  }

  public static void setSkippedShellcheckVersion(@NotNull String version) {
    if (StringUtil.isNotEmpty(version)) PropertiesComponent.getInstance().setValue(SHELLCHECK_SKIPPED_VERSION, version);
  }

  @NotNull
  public static String getSkippedShfmtVersion() {
    return PropertiesComponent.getInstance().getValue(SHFMT_SKIPPED_VERSION, "");
  }

  public static void setSkippedShfmtVersion(@NotNull String version) {
    if (StringUtil.isNotEmpty(version)) PropertiesComponent.getInstance().setValue(SHFMT_SKIPPED_VERSION, version);
  }
}
