// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.intellij.plugins.markdown.editor.CodeFenceLanguageListCompletionProvider;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;

public final class MarkdownTypedHandler extends TypedHandlerDelegate {
  @Override
  public @NotNull Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof MarkdownFile)) return Result.CONTINUE;
    if (charTyped == '`') {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      for (Caret caret : editor.getCaretModel().getAllCarets()) {
        final int offset = caret.getOffset();
        if (!CodeFenceLanguageListCompletionProvider.isInMiddleOfUnCollapsedFence(file.findElementAt(offset), offset)) {
          return Result.CONTINUE;
        }
      }

      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
      return Result.STOP;
    }
    if (charTyped == '<') {
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }
}
