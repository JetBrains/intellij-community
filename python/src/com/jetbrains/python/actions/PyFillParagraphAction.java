package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;

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

    performOnElement(element, editor);
  }

  protected void performOnElement(final PsiElement element, final Editor editor) {
    final Document document = editor.getDocument();

    final TextRange textRange = getTextRange(element, editor);
    final String text = textRange.substring(element.getContainingFile().getText());

    final List<String> strings = StringUtil.split(text, "\n", true);
    StringBuilder stringBuilder = new StringBuilder();
    for (String string : strings) {
      stringBuilder.append(StringUtil.trimStart(string.trim(), getPrefix(element))).append(" ");
    }
    final String newText = stringBuilder.toString();

    CommandProcessor.getInstance().executeCommand(element.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(),
                                   getPrefix(element) + newText);
            CodeStyleManager.getInstance(element.getProject()).reformatText(
              element.getContainingFile(),
              textRange.getStartOffset(),
              textRange.getStartOffset() + newText.length() + 1);
          }
        });
      }
    }, null, document);

  }

  protected String getPrefix(PsiElement element) {
    return element instanceof PsiComment? "#" : "";
  }

  private static TextRange getTextRange(PsiElement element, Editor editor) {

    if (element instanceof PsiComment) {
      PsiElement prev = getFirstElement(element);
      PsiElement next = getLastElement(element);

      final int startOffset = prev != null? prev.getTextRange().getStartOffset()
                                          : element.getTextRange().getStartOffset();
      final int endOffset = next != null? next.getTextRange().getEndOffset()
                                        : element.getTextRange().getEndOffset();
      return TextRange.create(startOffset, endOffset);
    }
    else {
      int startOffset = getStartOffset(element, editor);
      int endOffset = getEndOffset(element, editor);
      return TextRange.create(startOffset, endOffset);
    }
  }

  private static int getStartOffset(PsiElement element, Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    final int elementTextOffset = element.getTextOffset();
    final Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(offset);

    while (lineNumber != document.getLineNumber(elementTextOffset)) {
      final String text = document.getText(TextRange.create(document.getLineStartOffset(lineNumber),
                                                            document.getLineEndOffset(lineNumber)));
      if (StringUtil.isEmptyOrSpaces(text)) {
        break;
      }
      lineNumber -= 1;
    }
    final int lineStartOffset = document.getLineStartOffset(lineNumber + 1);
    final String lineText = document
      .getText(TextRange.create(lineStartOffset, document.getLineEndOffset(lineNumber + 1)));
    int shift = StringUtil.findFirst(lineText, CharFilter.NOT_WHITESPACE_FILTER);

    return lineStartOffset + shift;
  }

  private static int getEndOffset(PsiElement element, Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    final int elementTextOffset = element.getTextRange().getEndOffset();
    final Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(offset);

    while (lineNumber != document.getLineNumber(elementTextOffset) - 1) {
      final String text = document.getText(TextRange.create(document.getLineStartOffset(lineNumber),
                                                            document.getLineEndOffset(lineNumber)));
      if (StringUtil.isEmptyOrSpaces(text))
        break;
      lineNumber += 1;
    }
    return document.getLineEndOffset(lineNumber - 1);
  }

  private static PsiElement getFirstElement(final PsiElement element) {
    PsiElement e = element.getPrevSibling();
    PsiElement result = element;
    while (e instanceof PsiComment || e instanceof PsiWhiteSpace) {
      final String text = e.getText();
      if (StringUtil.countChars(text, '\n') > 1)
        break;
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
      final String text = e.getText();
      if (StringUtil.countChars(text, '\n') > 1)
        break;
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
