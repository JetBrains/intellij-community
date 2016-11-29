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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskDialogPanelProvider {

  private final static ExtensionPointName<TaskDialogPanelProvider> EP_NAME = ExtensionPointName.create("com.intellij.tasks.dialogPanelProvider");

  public static List<TaskDialogPanel> getOpenTaskPanels(@NotNull Project project, @NotNull Task task) {
    return ContainerUtil.mapNotNull(Extensions.getExtensions(EP_NAME),
                                    (NullableFunction<TaskDialogPanelProvider, TaskDialogPanel>)provider -> provider.getOpenTaskPanel(project, task));
  }
    
  public static List<TaskDialogPanel> getCloseTaskPanels(@NotNull Project project, @NotNull LocalTask task) {
    return ContainerUtil.mapNotNull(Extensions.getExtensions(EP_NAME),
                                    (NullableFunction<TaskDialogPanelProvider, TaskDialogPanel>)provider -> provider.getCloseTaskPanel(project, task));
  }
    
  @Nullable
  public abstract TaskDialogPanel getOpenTaskPanel(@NotNull Project project, @NotNull Task task);
  
  @Nullable
  public abstract TaskDialogPanel getCloseTaskPanel(@NotNull Project project, @NotNull LocalTask task);
}
