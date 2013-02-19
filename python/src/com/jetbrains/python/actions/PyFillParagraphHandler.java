package com.jetbrains.python.actions;

import com.intellij.codeInsight.editorActions.fillParagraph.ParagraphFillHandler;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class PyFillParagraphHandler extends ParagraphFillHandler {

  @NotNull
  protected String getPrefix(@NotNull final PsiElement element) {
    final PyStringLiteralExpression stringLiteralExpression =
      PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (stringLiteralExpression != null) {
      final Pair<String,String> quotes =
        PythonStringUtil.getQuotes(stringLiteralExpression.getText());
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyDocStringOwner.class);
      if (docStringOwner != null && stringLiteralExpression.equals(docStringOwner.getDocStringExpression())) {
        final PyStatementList statementList = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyStatementList.class);
        final PsiElement whiteSpace = statementList.getPrevSibling();
        String indent;
        if (whiteSpace instanceof PsiWhiteSpace)
          indent = whiteSpace.getText();
        else
          indent = "\n";
        return quotes != null? quotes.getFirst()+ indent : "\"" + indent;
      }
      else
        return quotes != null? quotes.getFirst() : "\"";
    }
    return element instanceof PsiComment? "# " : "";
  }

  @NotNull
  @Override
  protected String getPostfix(@NotNull PsiElement element) {
    final PyStringLiteralExpression stringLiteralExpression =
      PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (stringLiteralExpression != null) {
      final Pair<String,String> quotes =
        PythonStringUtil.getQuotes(stringLiteralExpression.getText());
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyDocStringOwner.class);
      if (docStringOwner != null && stringLiteralExpression.equals(docStringOwner.getDocStringExpression())) {
        final PyStatementList statementList = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyStatementList.class);
        final PsiElement whiteSpace = statementList.getPrevSibling();
        String indent;
        if (whiteSpace instanceof PsiWhiteSpace)
          indent = whiteSpace.getText();
        else
          indent = "\n";
        return quotes != null? indent + quotes.getSecond() : indent + "\"";
      }
      else
        return quotes != null? quotes.getSecond() : "\"";
    }
    return "";
  }

  @Override
  protected boolean isAvailableForElement(@Nullable PsiElement element) {
    if (element != null) {
      final PyStringLiteralExpression stringLiteral = PsiTreeUtil
        .getParentOfType(element, PyStringLiteralExpression.class);
      return stringLiteral != null || element instanceof PsiComment;
    }
    return false;
  }

  @Override
  protected boolean isAvailableForFile(@Nullable PsiFile psiFile) {
    return psiFile instanceof PyFile;
  }
}
