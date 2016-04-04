package com.jetbrains.edu;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import org.jetbrains.annotations.NotNull;

public class EduAnswerPlaceholderDeleteHandler implements ReadonlyFragmentModificationHandler {

  private final Editor myEditor;

  public EduAnswerPlaceholderDeleteHandler(@NotNull final Editor editor) {
    myEditor = editor;
  }

  @Override
  public void handle(ReadOnlyFragmentModificationException e) {
    HintManager.getInstance().showErrorHint(myEditor, "It's not allowed to delete answer placeholders");
  }
}
