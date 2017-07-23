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
import com.intellij.openapi.util.Key;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class ToggleBreadcrumbsAction extends ToggleAction implements DumbAware {

  static final class ShowHide extends ToggleBreadcrumbsAction {
    @Override
    boolean isEnabled(Editor editor) {
      return editor != null && super.isEnabled(editor);
    }
  }

  private static final Key<Boolean> FORCED_BREADCRUMBS = new Key<>("FORCED_BREADCRUMBS");

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    event.getPresentation().setEnabledAndVisible(isEnabled(findEditor(event)));
  }

  boolean isEnabled(Editor editor) {
    FileViewProvider provider = BreadcrumbsXmlWrapper.findViewProvider(editor);
    return provider == null || null != BreadcrumbsXmlWrapper.findInfoProvider(false, provider);
  }

  @Override
  public boolean isSelected(AnActionEvent event) {
    return isSelected(findEditor(event));
  }

  boolean isSelected(Editor editor) {
    if (editor != null) {
      Boolean selected = getForcedShown(editor);
      if (selected != null) return selected;
    }
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    boolean selected = settings.isBreadcrumbsShown();
    if (!selected) return false;

    String languageID = findLanguageID(editor);
    return languageID == null || settings.isBreadcrumbsShownFor(languageID);
  }

  @Override
  public void setSelected(AnActionEvent event, boolean selected) {
    if (setSelected(selected, findEditor(event))) {
      UISettings.getInstance().fireUISettingsChanged();
    }
  }

  boolean setSelected(Boolean selected, Editor editor) {
    if (editor == null) return false;
    Boolean old = getForcedShown(editor);
    editor.putUserData(FORCED_BREADCRUMBS, selected);
    return !Objects.equals(old, selected);
  }

  static Boolean getForcedShown(@NotNull Editor editor) {
    return editor.getUserData(FORCED_BREADCRUMBS);
  }

  static Editor findEditor(AnActionEvent event) {
    return event == null ? null : event.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
  }

  static String findLanguageID(Editor editor) {
    FileViewProvider provider = BreadcrumbsXmlWrapper.findViewProvider(editor);
    return provider == null ? null : provider.getBaseLanguage().getID();
  }
}
