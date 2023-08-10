// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;


public class RemoveTrailingBlankLinesFix implements LocalQuickFix, IntentionAction, HighPriorityAction {
  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("QFIX.remove.trailing.blank.lines");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    removeTrailingBlankLines(file);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    removeTrailingBlankLines(descriptor.getPsiElement().getContainingFile());
  }

  private static void removeTrailingBlankLines(PsiFile file) {
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return;
    }
    int lastBlankLineOffset = -1;
    for (int i = document.getLineCount() - 1; i >= 0; i--) {
      int lineStart = document.getLineStartOffset(i);
      String trimmed = document.getCharsSequence().subSequence(lineStart, document.getLineEndOffset(i)).toString().trim();
      if (trimmed.length() > 0) {
        break;
      }
      lastBlankLineOffset = lineStart;
    }
    if (lastBlankLineOffset > 0) {
      document.deleteString(lastBlankLineOffset, document.getTextLength());
    }
  }
}
