// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyUtil.StringNodeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: catherine
 * Intention to convert triple quoted string to single-quoted
 * For instance:
 * from:
 * a = """This line is ok,
 *      but "this" includes far too much
 *      whitespace at the start"""
 * to:
 * a = ("This line is ok," "\n"
 *      "but \"this\" includes far too much" "\n"
 *      "whitespace at the start")
 */
public final class PyConvertTripleQuotedStringIntention extends PsiUpdateModCommandAction<PyStringLiteralExpression> {
  PyConvertTripleQuotedStringIntention() {
    super(PyStringLiteralExpression.class);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.triple.quoted.string");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyStringLiteralExpression element) {
    final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
    if (docStringOwner != null) {
      if (docStringOwner.getDocStringExpression() == element) return null;
    }
    for (StringNodeInfo info : extractStringNodesInfo(element)) {
      if (info.isTripleQuoted() && info.isTerminated() && info.getNode().getTextRange().contains(context.offset())) {
        return super.getPresentation(context, element);
      }
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyStringLiteralExpression element, @NotNull ModPsiUpdater updater) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
    final StringBuilder result = new StringBuilder();
    final List<StringNodeInfo> nodeInfos = extractStringNodesInfo(element);
    for (int i = 0; i < nodeInfos.size(); i++) {
      final StringNodeInfo info = nodeInfos.get(i);
      List<String> lines = StringUtil.split(info.getContent(), "\n", true, false);
      boolean lastLineExcluded = false;
      if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
        lastLineExcluded = true;
        lines = lines.subList(0, lines.size() - 1);
      }

      final boolean inLastNode = i == nodeInfos.size() - 1;
      for (int j = 0; j < lines.size(); j++) {
        final String line = lines.get(j);
        final boolean inLastLine = j == lines.size() - 1;

        if (info.isRaw()) {
          appendSplittedRawStringLine(result, info, line);
          if (!inLastLine || lastLineExcluded) {
            result.append(" ").append(info.getSingleQuote()).append("\\n").append(info.getSingleQuote());
          }
        }
        else {
          result.append(info.getPrefix());
          result.append(info.getSingleQuote());
          result.append(convertToValidSubString(line, info.getSingleQuote(), info.isTripleQuoted()));
          if (!inLastLine || lastLineExcluded) {
            result.append("\\n");
          }
          result.append(info.getSingleQuote());
        }
        if (!(inLastNode && inLastLine)) {
          result.append("\n");
        }
      }
    }
    if (result.indexOf("\n") >= 0) {
      result.insert(0, "(");
      result.append(")");
    }
    PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), result.toString());

    final PsiElement parent = element.getParent();
    if (expression instanceof PyParenthesizedExpression &&
        (parent instanceof PyParenthesizedExpression ||
         parent instanceof PyTupleExpression ||
         parent instanceof PyArgumentList && ArrayUtil.getFirstElement(((PyArgumentList)parent).getArguments()) == element)) {
      expression = ((PyParenthesizedExpression)expression).getContainedExpression();
    }
    if (expression instanceof PyStringLiteralExpression && ((PyStringLiteralExpression)expression).isDocString()) {
      expression = elementGenerator.createStringLiteralAlreadyEscaped(result.toString());
    }
    if (expression != null) {
      element.replace(expression);
    }
  }

  private static void appendSplittedRawStringLine(@NotNull StringBuilder result, @NotNull StringNodeInfo info, @NotNull String line) {
    boolean singleQuoteUsed = false, doubleQuoteUsed = false;
    int chunkStart = 0;
    boolean firstChunk = true;
    for (int k = 0; k <= line.length(); k++) {
      if (k < line.length()) {
        singleQuoteUsed |= line.charAt(k) == '\'';
        doubleQuoteUsed |= line.charAt(k) == '"';
      }
      final char chunkQuote;
      if ((k == line.length() || line.charAt(k) == '\'') && doubleQuoteUsed) {
        chunkQuote = '\'';
        doubleQuoteUsed = false;
      }
      else if ((k == line.length() || line.charAt(k) == '"') && singleQuoteUsed) {
        chunkQuote = '"';
        singleQuoteUsed = false;
      }
      else if (k == line.length()) {
        chunkQuote = info.getSingleQuote();
      }
      else {
        continue;
      }
      if (!firstChunk) {
        result.append(" ");
      }
      result.append(info.getPrefix()).append(chunkQuote).append(line, chunkStart, k).append(chunkQuote);
      firstChunk = false;
      chunkStart = k;
    }
  }

  @NotNull
  private static String convertToValidSubString(@NotNull String content, char newQuote, boolean isMultiline) {
    return isMultiline ? StringUtil.escapeChar(content, newQuote) : content;
  }

  @NotNull
  private static List<StringNodeInfo> extractStringNodesInfo(@NotNull PyStringLiteralExpression expression) {
    return ContainerUtil.map(expression.getStringNodes(), node -> new StringNodeInfo(node));
  }
}
