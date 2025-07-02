package com.intellij.sh.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ShShfmtFormatterUtilBase {
  boolean isValidPath(@Nullable String path);

  void download(@NotNull Project project, @NotNull Runnable onSuccess, @NotNull Runnable onFailure);

  void checkShfmtForUpdate(@NotNull Project project);

  static ShShfmtFormatterUtilBase getInstance() {
    return ApplicationManager.getApplication().getService(ShShfmtFormatterUtilBase.class);
  }
}