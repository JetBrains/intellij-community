package com.jetbrains.python.formatter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.GenericLineWrapPositionStrategy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyLineWrapPositionStrategy extends GenericLineWrapPositionStrategy {
  public PyLineWrapPositionStrategy() {
    // Commas.
    addRule(new Rule(',', WrapCondition.AFTER, Rule.DEFAULT_WEIGHT * 1.1));

    // Symbols to wrap either before or after.
    addRule(new Rule(' '));
    addRule(new Rule('\t'));

    // Symbols to wrap after.
    addRule(new Rule('(', WrapCondition.AFTER));
    addRule(new Rule('[', WrapCondition.AFTER));
    addRule(new Rule('{', WrapCondition.AFTER));

    // Symbols to wrap before
    addRule(new Rule('.', WrapCondition.BEFORE));
  }

  @Override
  protected boolean canUseOffset(@NotNull Document document, int offset, boolean virtual) {
    if (virtual) {
      return true;
    }
    CharSequence text = document.getCharsSequence();
    char c = text.charAt(offset);
    if (!StringUtil.isWhiteSpace(c)) {
      return true;
    }

    int i = CharArrayUtil.shiftBackward(text, offset, " \t");
    if (i < 2) {
      return true;
    }
    return text.charAt(i - 2) != 'd' || text.charAt(i - 1) != 'e' || text.charAt(i) != 'f';
  }

  @Override
  public int calculateWrapPosition(@NotNull Document document,
                                   @Nullable Project project,
                                   int startOffset,
                                   int endOffset,
                                   int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset,
                                   boolean virtual) {
    int wrapPosition =
      super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset, virtual);
    if (wrapPosition < 0) return wrapPosition;
    final CharSequence text = document.getCharsSequence();

    char c = text.charAt(wrapPosition);
    if (!StringUtil.isWhiteSpace(c) || project == null) {
      return wrapPosition;
    }

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (documentManager != null) {
      final PsiFile psiFile = documentManager.getPsiFile(document);
      if (psiFile != null) {
        final PsiElement element = psiFile.findElementAt(wrapPosition);
        final StringLiteralExpression string = PsiTreeUtil.getParentOfType(element, StringLiteralExpression.class);

        if (string != null) {
          return wrapPosition + 1;
        }
      }
    }
    return wrapPosition;
  }
}
