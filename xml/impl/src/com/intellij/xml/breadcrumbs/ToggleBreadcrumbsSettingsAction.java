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
import com.intellij.psi.FileViewProvider;

final class ToggleBreadcrumbsSettingsAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(AnActionEvent event) {
    return isSelected(findEditor(event));
  }

  @Override
  public void setSelected(AnActionEvent event, boolean selected) {
    boolean modified;

    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();

    String languageID = findLanguageID(!selected ? null : findEditor(event));
    if (languageID == null) {
      modified = settings.setBreadcrumbsShown(selected);
    }
    else {
      modified = settings.setBreadcrumbsShownFor(languageID, selected);
      if (selected && settings.setBreadcrumbsShown(true)) modified = true;
    }
    if (modified) {
      UISettings.getInstance().fireUISettingsChanged();
    }
  }

  static boolean isSelected(Editor editor) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();

    boolean selected = settings.isBreadcrumbsShown();
    if (!selected) return false;

    String languageID = findLanguageID(editor);
    return languageID == null || settings.isBreadcrumbsShownFor(languageID);
  }

  static Editor findEditor(AnActionEvent event) {
    return event == null ? null : event.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
  }

  private static String findLanguageID(Editor editor) {
    FileViewProvider provider = BreadcrumbsXmlWrapper.findViewProvider(editor);
    return provider == null ? null : provider.getBaseLanguage().getID();
  }
}
