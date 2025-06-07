// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
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
  public void actionPerformed(final @NotNull AnActionEvent e) {
    IdeFocusManager.getGlobalInstance()
      .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getNext(e).getContentComponent(), true));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = getNext(e) != null;
    e.getPresentation().setEnabled(enabled);
  }

  protected Editor getNext(@NotNull AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      return null;
    }
    return editor.getUserData(myNext ? NEXT_EDITOR_KEY : PREV_EDITOR_KEY);
  }
}
