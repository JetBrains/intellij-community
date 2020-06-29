package org.intellij.plugins.markdown.editor;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.intellij.plugins.markdown.injection.LanguageListCompletionContributor;
import org.jetbrains.annotations.NotNull;

public class MarkdownTypedHandler extends TypedHandlerDelegate {
  @NotNull
  @Override
  public Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (charTyped == '`') {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      for (Caret caret : editor.getCaretModel().getAllCarets()) {
        final int offset = caret.getOffset();
        if (!LanguageListCompletionContributor.isInMiddleOfUnCollapsedFence(file.findElementAt(offset), offset)) {
          return Result.CONTINUE;
        }
      }

      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
      return Result.STOP;
    }

    return super.checkAutoPopup(charTyped, project, editor, file);
  }
}
