/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  
  @NotNull
  public abstract JComponent getPanel();
  
  @Nullable
  public JComponent getPreferredFocusedComponent() { return null; }
  
  @Nullable
  public ValidationInfo validate() { return null; }
  
  public abstract void commit();

  public void taskNameChanged(Task oldTask, Task newTask) {}
}
