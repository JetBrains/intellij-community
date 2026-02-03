// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
class ResourceBundleEditorKeepEmptyValueToggleAction extends CheckboxAction {
  private static final String SELECTION_KEY = "resource.bundle.editor.insert.empty.values";

  ResourceBundleEditorKeepEmptyValueToggleAction() {
    super(ResourceBundleEditorBundle.message("action.keep.empty.value.description"),
          ResourceBundleEditorBundle.message("action.keep.empty.value.text"), null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return keepEmptyProperties();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    PropertiesComponent.getInstance().setValue(SELECTION_KEY, state, true);
  }

  public static boolean keepEmptyProperties() {
    return PropertiesComponent.getInstance().getBoolean(SELECTION_KEY, true);
  }
}
