// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class LookForNestedToggleAction extends ToggleAction {
  private static final String CONFIG_LOOK_FOR_NESTED = "maven.config.look.for.nested";

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isSelected();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setSelected(state);
  }

  public static boolean isSelected() {
    return PropertiesComponent.getInstance().getBoolean(CONFIG_LOOK_FOR_NESTED);
  }

  public static void setSelected(boolean state) {
    PropertiesComponent.getInstance().setValue(CONFIG_LOOK_FOR_NESTED, state);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
