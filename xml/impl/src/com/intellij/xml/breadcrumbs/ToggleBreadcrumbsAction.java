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
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

final class ToggleBreadcrumbsAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(AnActionEvent event) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();

    boolean selected = settings.isBreadcrumbsShown();
    if (!selected) return false;

    String languageID = findLanguageID(event);
    return languageID == null || settings.isBreadcrumbsShownFor(languageID);
  }

  @Override
  public void setSelected(AnActionEvent event, boolean selected) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();

    boolean modified;
    String languageID = findLanguageID(event);
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

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);

    Editor editor = findEditor(event);
    event.getPresentation().setEnabled(editor != null);
  }

  private static Editor findEditor(AnActionEvent event) {
    return event == null ? null : event.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
  }

  private static String findLanguageID(AnActionEvent event) {
    if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) return null;
    FileViewProvider provider = BreadcrumbsXmlWrapper.findViewProvider(findEditor(event));
    return provider == null ? null : provider.getBaseLanguage().getID();
  }
}
