// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.intellij.plugins.markdown.lang.MarkdownLanguageUtilsKt;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;

public final class MarkdownTypedHandler extends TypedHandlerDelegate {
  @Override
  public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    if (c != ']' || !(file instanceof MarkdownFile)) return Result.CONTINUE;
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    if (!iterator.atEnd() && iterator.getTokenType() == MarkdownTokenTypes.CHECK_BOX && iterator.getEnd() - 1 == offset) {
      EditorModificationUtil.moveCaretRelatively(editor, 1);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  @Override
  public @NotNull Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!MarkdownLanguageUtilsKt.supportsMarkdown(file.getLanguage(), null)) return Result.CONTINUE;
    if (charTyped == '@') {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      return Result.STOP;
    }
    if (charTyped == '`') {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      for (Caret caret : editor.getCaretModel().getAllCarets()) {
        final int offset = caret.getOffset();
        if (!CodeFenceLanguageListCompletionProvider.isInMiddleOfUnCollapsedFence(file.findElementAt(offset), offset)) {
          return Result.CONTINUE;
        }
      }

      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      return Result.STOP;
    }
    if (charTyped == '<') {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }
}
