package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;

import java.util.List;

/**
 * User : ktisha
 */
public class PyFillParagraphAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;

    PsiElement element = e.getData(LangDataKeys.PSI_ELEMENT);
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (element == null) {
      final PsiElement file = e.getData(LangDataKeys.PSI_FILE);

      if (file != null && editor != null) {
        final int offset = editor.getCaretModel().getOffset();
        element = file.findElementAt(offset);
      }
    }

    if (element instanceof PsiComment) {
      processComment(element, editor.getDocument());
    }
    else {
      element = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);

      if (element != null) {
        final String text = element.getText();
        final List<String> substrings = StringUtil.split(text, "\n", true);
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : substrings) {
          stringBuilder.append(StringUtil.trimStart(string.trim(), getCommentPrefix())).append(" ");
        }
        String replacementString = stringBuilder.toString();
        final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        final PsiElement docstring = elementGenerator.createFromText(
                                                    LanguageLevel.forElement(element), PyExpressionStatement.class,
                                                    replacementString).getExpression();
        final PsiElement finalElement = element;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            PsiElement replacementElement = finalElement.replace(docstring);
            replacementElement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(replacementElement);

            final TextRange textRange = getTextRange(replacementElement);
            CodeStyleManager.getInstance(project).reformatText(
              replacementElement.getContainingFile(), textRange.getStartOffset(),
                                                      textRange.getEndOffset());
          }
        });

      }
    }
  }

  protected void processComment(final PsiElement element, final Document document) {
    final TextRange textRange = getTextRange(element);
    final String text = textRange.substring(element.getContainingFile().getText());

    final List<String> strings = StringUtil.split(text, "\n", true);
    StringBuilder stringBuilder = new StringBuilder();
    for (String string : strings) {
      stringBuilder.append(StringUtil.trimStart(string.trim(), getCommentPrefix())).append(" ");
    }
    final String newText = stringBuilder.toString();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), getCommentPrefix() + newText);
        CodeStyleManager.getInstance(element.getProject()).reformatText(
          element.getContainingFile(),
          textRange.getStartOffset(),
          textRange.getStartOffset() + newText.length() + 1);
      }
    });

  }

  protected String getCommentPrefix() {
    return "#";
  }

  private static TextRange getTextRange(PsiElement element) {
    if (element instanceof PyStringLiteralExpression)
      return element.getTextRange();

    PsiElement prev = getFirstElement(element);
    PsiElement next = getLastElement(element);

    final int startOffset = prev != null? prev.getTextRange().getStartOffset()
                                        : element.getTextRange().getStartOffset();
    final int endOffset = next != null? next.getTextRange().getEndOffset()
                                      : element.getTextRange().getEndOffset();
    return TextRange.create(startOffset, endOffset);
  }

  private static PsiElement getFirstElement(final PsiElement element) {
    PsiElement e = element.getPrevSibling();
    PsiElement result = element;
    while (e instanceof PsiComment || e instanceof PsiWhiteSpace) {
      if (e instanceof PsiComment)
        result = e;
      e = e.getPrevSibling();
    }
    return result;
  }

  private static PsiElement getLastElement(final PsiElement element) {
    PsiElement e = element.getNextSibling();
    PsiElement result = element;
    while (e instanceof PsiComment || e instanceof PsiWhiteSpace) {
      if (e instanceof PsiComment)
        result = e;
      e = e.getNextSibling();
    }
    return result;
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
