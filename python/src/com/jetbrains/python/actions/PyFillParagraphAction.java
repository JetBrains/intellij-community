package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * User : ktisha
 */
public class PyFillParagraphAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;

    PsiElement element = e.getData(LangDataKeys.PSI_ELEMENT);
    if (element == null) {
      final PsiElement file = e.getData(LangDataKeys.PSI_FILE);
      Editor editor = e.getData(PlatformDataKeys.EDITOR);
      if (file != null && editor != null) {
        final int offset = editor.getCaretModel().getOffset();
        element = file.findElementAt(offset);
      }
    }

    if (!(element instanceof PsiComment))
      element = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);

    if (element != null) {
      final TextRange textRange = getTextRange(element);
      final PsiElement finalElement = element;
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          CodeStyleManager.getInstance(project).reformatRange(finalElement.getContainingFile(), textRange.getStartOffset(),
                                                              textRange.getEndOffset());
        }
      });

    }
  }

  private static TextRange getTextRange(PsiElement element) {
    if (element instanceof PyStringLiteralExpression)
      return element.getTextRange();

    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(element, PsiComment.class);
    PsiElement next = PsiTreeUtil.skipSiblingsForward(element, PsiComment.class);

    final int startOffset = prev != null? prev.getTextRange().getStartOffset() : element.getTextRange().getStartOffset();
    final int endOffset = next != null? next.getTextRange().getEndOffset() : element.getTextRange().getEndOffset();
    return TextRange.create(startOffset, endOffset);
  }


  @Override
  public void update(AnActionEvent e) {
    PsiElement element = e.getData(LangDataKeys.PSI_ELEMENT);
    final PsiElement file = e.getData(LangDataKeys.PSI_FILE);
    if (!(file instanceof PyFile)) {
      e.getPresentation().setEnabled(false);
      return;
    }
    if (element == null) {
      Editor editor = e.getData(PlatformDataKeys.EDITOR);
      if (editor != null) {
      final int offset = editor.getCaretModel().getOffset();
        element = file.findElementAt(offset);
      }
    }
    if (element != null) {
      final PyStringLiteralExpression stringLiteral = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
      e.getPresentation().setEnabled(stringLiteral != null || element instanceof PsiComment);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }
}
