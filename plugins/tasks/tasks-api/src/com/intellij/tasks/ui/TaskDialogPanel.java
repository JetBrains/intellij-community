// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.tasks.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskDialogPanel {
  
  public abstract @NotNull JComponent getPanel();
  
  public @Nullable JComponent getPreferredFocusedComponent() { return null; }
  
  public @Nullable ValidationInfo validate() { return null; }
  
  public abstract void commit();

  public void taskNameChanged(Task oldTask, Task newTask) {}
}
