package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.Executor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class RunAnythingItem {
  public abstract void run(@NotNull Executor executor, @Nullable VirtualFile workDirectory);

  public abstract String getText();

  public abstract Icon getIcon();

  @NotNull
  public static String getActualWorkDirectory(@NotNull Project project, @Nullable VirtualFile workDirectory) {
    //noinspection ConstantConditions
    return workDirectory == null ? project.getBasePath() : workDirectory.getPath();
  }
}
