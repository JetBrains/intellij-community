// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveTrailingBlankLinesFix implements ModCommandAction {
  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.psiUpdate(context.file(), (file, updater) -> {
      removeTrailingBlankLines(file);
    });
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("QFIX.remove.trailing.blank.lines");
  }

  private static void removeTrailingBlankLines(PsiFile file) {
    Document document = file.getFileDocument();
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
