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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;

/**
 * @author Dmitry Batkovich
 */
class ResourceBundleEditorKeepEmptyValueToggleAction extends CheckboxAction {
  private final static String SELECTION_KEY = "resource.bundle.editor.insert.empty.values";

  public ResourceBundleEditorKeepEmptyValueToggleAction() {
    super("Do not insert properties with empty value",
          "Do not create value if value text field is empty. Delete existed value if text field become empty.", null);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return keepEmptyProperties();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    PropertiesComponent.getInstance().setValue(SELECTION_KEY, state, true);
  }

  public static boolean keepEmptyProperties() {
    return PropertiesComponent.getInstance().getBoolean(SELECTION_KEY, true);
  }
}
