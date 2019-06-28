// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.sh.highlighting.ShTextOccurrencesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ShRenameAllOccurrencesHandler extends EditorActionHandler {
  public static final ShRenameAllOccurrencesHandler INSTANCE = new ShRenameAllOccurrencesHandler();

  private ShRenameAllOccurrencesHandler() {
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return editor.getProject() != null;
  }

  @Override
  public void doExecute(@NotNull final Editor editor, @Nullable Caret c, DataContext dataContext) {
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    SelectionModel selectionModel = editor.getSelectionModel();
    boolean hasSelection = caret.hasSelection();
    TextRange caretTextRange = hasSelection
        ? new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd())
        : ShTextOccurrencesUtil.findTextRangeOfIdentifierAtCaret(editor);
    if (caretTextRange == null) return;
    CharSequence documentText = editor.getDocument().getImmutableCharSequence();
    CharSequence textToFind = caretTextRange.subSequence(documentText);
    Collection<TextRange> occurrences = ShTextOccurrencesUtil.findAllOccurrences(documentText, textToFind, !hasSelection);
    Project project = editor.getProject();
    assert project != null;
    ShTextRenameRefactoring rename = ShTextRenameRefactoring.create(editor, project, textToFind.toString(), occurrences, caretTextRange);
    if (rename != null) {
      rename.start();
    }
  }
}
