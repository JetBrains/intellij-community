/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  private final static String SELECTION_KEY = "resource.bundle.editor.insert.empty.values";

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
