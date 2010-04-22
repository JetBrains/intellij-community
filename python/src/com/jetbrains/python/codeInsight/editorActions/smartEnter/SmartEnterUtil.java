package com.jetbrains.python.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   19:48:16
 */
public class SmartEnterUtil {
  private static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

  public static void plainEnter(@NotNull final Editor editor) {
    getEnterHandler().execute(editor, ((EditorEx) editor).getDataContext());
  }

  private SmartEnterUtil() {
  }
}
