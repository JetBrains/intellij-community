// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions;

import com.intellij.codeInsight.editorActions.fillParagraph.ParagraphFillHandler;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User : ktisha
 */
public final class PyFillParagraphHandler extends ParagraphFillHandler {

  @Override
  @NotNull
  protected String getPrefix(@NotNull final PsiElement element) {
    final PyStringLiteralExpression stringLiteralExpression =
      PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (stringLiteralExpression != null) {
      final String text = stringLiteralExpression.getText();
      final Pair<String,String> quotes =
        PyStringLiteralCoreUtil.getQuotes(text);
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyDocStringOwner.class);
      if (docStringOwner != null && stringLiteralExpression.equals(docStringOwner.getDocStringExpression())) {
        String indent = getIndent(stringLiteralExpression);
        if (quotes != null) {
          final List<String> strings = StringUtil.split(text, "\n");
          if (strings.get(0).trim().equals(quotes.getFirst())) {
            return quotes.getFirst() + indent;
          }
          else {
            final String value = stringLiteralExpression.getStringValue();
            final int firstNotSpace = StringUtil.findFirst(value, CharFilter.NOT_WHITESPACE_FILTER);
            return quotes.getFirst() + value.substring(0, firstNotSpace);
          }
        }
        return "\"" + indent;
      }
      else
        return quotes != null? quotes.getFirst() : "\"";
    }
    return element instanceof PsiComment? "# " : "";
  }

  private static String getIndent(PyStringLiteralExpression stringLiteralExpression) {
    final PyStatementList statementList = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyStatementList.class);
    String indent = "";
    if (statementList != null) {
      final PsiElement whiteSpace = statementList.getPrevSibling();
      if (whiteSpace instanceof PsiWhiteSpace)
        indent = whiteSpace.getText();
      else
        indent = "\n";
    }
    return indent;
  }

  @NotNull
  @Override
  protected String getPostfix(@NotNull PsiElement element) {
    final PyStringLiteralExpression stringLiteralExpression =
      PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (stringLiteralExpression != null) {
      final String text = stringLiteralExpression.getText();
      final Pair<String,String> quotes =
        PyStringLiteralCoreUtil.getQuotes(text);
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteralExpression, PyDocStringOwner.class);
      if (docStringOwner != null && stringLiteralExpression.equals(docStringOwner.getDocStringExpression())) {
        String indent = getIndent(stringLiteralExpression);
        if (quotes != null) {
          final List<String> strings = StringUtil.split(text, "\n");
          if (strings.get(strings.size()-1).trim().equals(quotes.getSecond())) {
            return indent + quotes.getSecond();
          }
          else {
            return quotes.getSecond();
          }
        }
        return indent + "\"";
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
