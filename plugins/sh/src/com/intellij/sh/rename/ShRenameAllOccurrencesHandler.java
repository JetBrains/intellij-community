// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.sh.highlighting.ShTextOccurrencesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

class ShRenameAllOccurrencesHandler extends EditorActionHandler {
  public static final ShRenameAllOccurrencesHandler INSTANCE = new ShRenameAllOccurrencesHandler();
  static final String MAX_SEGMENTS_PROP_NAME = "sh.max.inplace.rename.segments";
  static final Key<TextOccurrencesRenamer> RENAMER_KEY = Key.create("renamer");

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
    TextRange occurrenceAtCaret = hasSelection
        ? new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd())
        : ShTextOccurrencesUtil.findTextRangeOfIdentifierAtCaret(editor);
    if (occurrenceAtCaret == null) return;
    CharSequence documentText = editor.getDocument().getImmutableCharSequence();
    CharSequence textToFind = occurrenceAtCaret.subSequence(documentText);
    Collection<TextRange> occurrences = ShTextOccurrencesUtil.findAllOccurrences(documentText, textToFind, !hasSelection);
    Project project = editor.getProject();
    assert project != null;
    if (occurrences.size() < getMaxInplaceRenameSegments() && documentText.length() < FileUtilRt.MEGABYTE) {
      ShTextRenameRefactoring rename = ShTextRenameRefactoring.create(editor, project, textToFind.toString(), occurrences, occurrenceAtCaret);
      if (rename != null) {
        rename.start();
        return;
      }
    }
    TextOccurrencesRenamer renamer = new TextOccurrencesRenamer(editor, textToFind.toString(), occurrences, occurrenceAtCaret);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      editor.putUserData(RENAMER_KEY, renamer);
    }
    else {
      new ShRenameDialog(project, renamer).show();
    }
  }

  private static int getMaxInplaceRenameSegments() {
    return StringUtil.parseInt(System.getProperty(MAX_SEGMENTS_PROP_NAME), 300);
  }
}
