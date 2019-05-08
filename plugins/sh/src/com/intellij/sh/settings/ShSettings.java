package com.intellij.sh.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShSettings {
  private static final String SHELLCHECK_PATH = "SHELLCHECK.PATH";

  @NotNull
  public static String getShellcheckPath() {
    return PropertiesComponent.getInstance().getValue(SHELLCHECK_PATH, "");
  }

  public static void setShellcheckPath(@Nullable String path) {
    if (StringUtil.isNotEmpty(path)) PropertiesComponent.getInstance().setValue(SHELLCHECK_PATH, path);
  }
}
