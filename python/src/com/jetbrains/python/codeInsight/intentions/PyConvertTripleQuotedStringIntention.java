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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyUtil.StringNodeInfo;
import org.jetbrains.annotations.NotNull;

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
public class PyConvertTripleQuotedStringIntention extends PyBaseIntentionAction {

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.triple.quoted.string");
  }

  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("INTN.triple.quoted.string");
  }

  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    final int caretOffset = editor.getCaretModel().getOffset();
    final PyStringLiteralExpression pyString = PsiTreeUtil.getParentOfType(file.findElementAt(caretOffset), PyStringLiteralExpression.class);
    if (pyString != null) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(pyString, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == pyString) return false;
      }
      for (StringNodeInfo info : extractStringNodesInfo(pyString)) {
        if (info.isTripleQuoted() && info.isTerminated() && info.getNode().getTextRange().contains(caretOffset)) {
          return true;
        }
      }
    }
    return false;
  }

  public void doInvoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
    final PyStringLiteralExpression pyString = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                           PyStringLiteralExpression.class);
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (pyString != null) {
      final StringBuilder result = new StringBuilder();
      final List<StringNodeInfo> nodeInfos = extractStringNodesInfo(pyString);
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
      PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(pyString), result.toString());

      final PsiElement parent = pyString.getParent();
      if (expression instanceof PyParenthesizedExpression &&
          (parent instanceof PyParenthesizedExpression ||
           parent instanceof PyTupleExpression ||
           parent instanceof PyArgumentList && ArrayUtil.getFirstElement(((PyArgumentList)parent).getArguments()) == pyString)) {
        expression = ((PyParenthesizedExpression)expression).getContainedExpression();
      }
      if (expression != null) {
        pyString.replace(expression);
      }
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
