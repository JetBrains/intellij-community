package com.intellij.tasks;

import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public interface CustomTaskState {
  @NotNull
  String getId();

  @NotNull
  String getPresentableName();
}
