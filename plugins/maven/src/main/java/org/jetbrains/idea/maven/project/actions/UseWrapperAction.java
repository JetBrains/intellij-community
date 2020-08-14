// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class UseWrapperAction extends ToggleAction {
  private final static String DISABLE_MAVEN_WRAPPER = "maven.wrapper.disable";

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return canUseWrapper();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setSelected(state);
  }

  public static boolean canUseWrapper() {
    return !PropertiesComponent.getInstance().getBoolean(DISABLE_MAVEN_WRAPPER);
  }

  private static void setSelected(boolean state) {
    PropertiesComponent.getInstance().setValue(DISABLE_MAVEN_WRAPPER, !state);
  }
}
