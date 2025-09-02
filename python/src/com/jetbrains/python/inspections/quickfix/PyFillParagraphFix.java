// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.editorActions.fillParagraph.FillParagraphAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class PyFillParagraphFix extends BaseIntentionAction implements  HighPriorityAction {

  public PyFillParagraphFix() {
    setText(PyBundle.message("QFIX.fill.paragraph"));
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    final FillParagraphAction action = new FillParagraphAction();
    action.actionPerformedImpl(project, editor);
  }
}
