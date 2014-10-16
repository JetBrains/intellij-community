/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User : ktisha
 */
public class PyFillParagraphHandler extends ParagraphFillHandler {

  @NotNull
  protected String getPrefix(@NotNull final PsiElement element) {
    final PyStringLiteralExpression stringLiteralExpression =
      PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (stringLiteralExpression != null) {
      final String text = stringLiteralExpression.getText();
      final Pair<String,String> quotes =
        PythonStringUtil.getQuotes(text);
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
        PythonStringUtil.getQuotes(text);
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
