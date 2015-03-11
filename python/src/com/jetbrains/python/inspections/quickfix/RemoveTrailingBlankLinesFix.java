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
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RemoveTrailingBlankLinesFix implements LocalQuickFix, IntentionAction, HighPriorityAction {
  @NotNull
  @Override
  public String getText() {
    return "Remove trailing blank lines";
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
  public String getName() {
    return getText();
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
