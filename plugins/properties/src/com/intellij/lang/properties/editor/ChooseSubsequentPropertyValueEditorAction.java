/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class ChooseSubsequentPropertyValueEditorAction extends AnAction {
  public static final Key<Editor> NEXT_EDITOR_KEY = Key.create("resourceBundleEditor.nextEditor");
  public static final Key<Editor> PREV_EDITOR_KEY = Key.create("resourceBundleEditor.prevEditor");

  public final boolean myNext;

  public static class Next extends ChooseSubsequentPropertyValueEditorAction {
    public Next() {
      super(true);
    }
  }

  public static class Prev extends ChooseSubsequentPropertyValueEditorAction {
    public Prev() {
      super(false);
    }
  }

  private ChooseSubsequentPropertyValueEditorAction(final boolean next) {
    myNext = next;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    getNext(e).getContentComponent().requestFocus();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = getNext(e) != null;
    e.getPresentation().setEnabled(enabled);
  }

  protected Editor getNext(AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      return null;
    }
    return editor.getUserData(myNext ? NEXT_EDITOR_KEY : PREV_EDITOR_KEY);
  }
}
