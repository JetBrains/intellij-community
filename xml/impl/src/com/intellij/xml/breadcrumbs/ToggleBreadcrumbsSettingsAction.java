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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;

class ToggleBreadcrumbsSettingsAction extends ToggleBreadcrumbsAction {

  static final class ShowAbove extends ToggleBreadcrumbsSettingsAction {
    public ShowAbove() {
      super(true, true);
    }
  }

  static final class ShowBelow extends ToggleBreadcrumbsSettingsAction {
    public ShowBelow() {
      super(true, false);
    }
  }

  static final class HideBoth extends ToggleBreadcrumbsSettingsAction {
    public HideBoth() {
      super(false, false);
    }
  }

  private final boolean show;
  private final boolean above;

  private ToggleBreadcrumbsSettingsAction(boolean show, boolean above) {
    this.show = show;
    this.above = above;
  }

  @Override
  boolean isSelected(Editor editor) {
    boolean selected = super.isSelected(editor);
    if (!show && !selected) return true;
    if (!show || !selected) return false;
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    return above == settings.isBreadcrumbsAbove();
  }

  @Override
  boolean setSelected(Boolean selected, Editor editor) {
    boolean modified = super.setSelected(null, editor);
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings.setBreadcrumbsShown(show)) modified = true;
    if (show) {
      if (settings.setBreadcrumbsAbove(above)) modified = true;
      String languageID = findLanguageID(editor);
      if (languageID != null && settings.setBreadcrumbsShownFor(languageID, true)) modified = true;
    }
    return modified;
  }
}
