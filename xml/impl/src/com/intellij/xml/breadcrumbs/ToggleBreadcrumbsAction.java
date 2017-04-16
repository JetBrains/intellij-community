/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class ToggleBreadcrumbsAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(AnActionEvent event) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    return settings.isBreadcrumbsShown();
  }

  @Override
  public void setSelected(AnActionEvent event, boolean selected) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings.setBreadcrumbsShown(selected)) {
      UISettings.getInstance().fireUISettingsChanged();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);

    Editor editor = event.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    event.getPresentation().setEnabled(editor != null);
  }
}
