// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShSettings {
  public static final String I_DO_MIND = "I do mind";

  private static final String SHELLCHECK_PATH = "SHELLCHECK.PATH";
  private static final String SHFMT_PATH = "SHFMT.PATH";

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
}
