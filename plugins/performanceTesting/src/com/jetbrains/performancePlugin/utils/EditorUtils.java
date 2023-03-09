package com.jetbrains.performancePlugin.utils;

import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public final class EditorUtils {

  public static DataContext createEditorContext(@NotNull Editor editor) {
    Editor hostEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
    DataContext parent = DataManager.getInstance().getDataContext(editor.getContentComponent());
    return SimpleDataContext.builder()
      .setParent(parent)
      .add(CommonDataKeys.HOST_EDITOR, hostEditor)
      .add(CommonDataKeys.EDITOR, editor)
      .build();
  }

}
