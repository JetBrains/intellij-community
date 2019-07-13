// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShLanguage;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

abstract class ShBaseGenerateAction extends CodeInsightAction implements CodeInsightActionHandler {
  protected static final String FEATURE_ACTION_ID = "GenerateActionUsed";

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return file.getLanguage().is(ShLanguage.INSTANCE);
  }

  protected static void moveAtNewLineIfNeeded(@NotNull Editor editor) {
    Document document = editor.getDocument();
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    int line = caret.getLogicalPosition().line;

    if (DocumentUtil.isLineEmpty(document, line)) return;

    int lineEndOffset = DocumentUtil.getLineEndOffset(caret.getOffset(), document);
    caret.moveToOffset(lineEndOffset);

    EditorActionHandler actionHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    actionHandler.execute(editor, caret, ((EditorEx)editor).getDataContext());
  }
}
