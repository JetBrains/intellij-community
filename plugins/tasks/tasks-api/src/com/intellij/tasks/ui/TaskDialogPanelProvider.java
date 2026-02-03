// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.LocalTask;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskDialogPanelProvider {

  private static final ExtensionPointName<TaskDialogPanelProvider> EP_NAME = ExtensionPointName.create("com.intellij.tasks.dialogPanelProvider");

  public static List<TaskDialogPanel> getOpenTaskPanels(@NotNull Project project, @NotNull LocalTask task) {
    return ContainerUtil.mapNotNull(EP_NAME.getExtensionList(),
                                    (NullableFunction<TaskDialogPanelProvider, TaskDialogPanel>)provider -> provider.getOpenTaskPanel(project, task));
  }

  public static List<TaskDialogPanel> getCloseTaskPanels(@NotNull Project project, @NotNull LocalTask task) {
    return ContainerUtil.mapNotNull(EP_NAME.getExtensionList(),
                                    (NullableFunction<TaskDialogPanelProvider, TaskDialogPanel>)provider -> provider.getCloseTaskPanel(project, task));
  }

  public abstract @Nullable TaskDialogPanel getOpenTaskPanel(@NotNull Project project, @NotNull LocalTask task);

  public abstract @Nullable TaskDialogPanel getCloseTaskPanel(@NotNull Project project, @NotNull LocalTask task);
}
