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

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
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
public class PyConvertTripleQuotedStringIntention extends BaseIntentionAction {

  public static final String TRIPLE_SINGLE_QUOTE = "'''";
  public static final String TRIPLE_DOUBLE_QUOTE = "\"\"\"";

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
    final PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(file.findElementAt(caretOffset), PyStringLiteralExpression.class);
    if (string != null) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string) return false;
      }

      for (StringNodeInfo info : extractStringNodesInfo(string)) {
        if (info.isTripleQuoted && info.node.getTextRange().contains(caretOffset)) {
          return true;
        }
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
    final PyStringLiteralExpression pyString = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                           PyStringLiteralExpression.class);
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (pyString != null) {
      final StringBuilder result = new StringBuilder();
      final List<StringNodeInfo> nodeInfos = extractStringNodesInfo(pyString);
      for (int i = 0; i < nodeInfos.size(); i++) {
        final StringNodeInfo info = nodeInfos.get(i);
        List<String> lines = StringUtil.split(info.content, "\n", true, false);
        boolean lastLineExcluded = false;
        if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
          lastLineExcluded = true;
          lines = lines.subList(0, lines.size() - 1);
        }

        final boolean inLastNode = i == nodeInfos.size() - 1;
        for (int j = 0; j < lines.size(); j++) {
          final String line = lines.get(j);
          final boolean inLastLine = j == lines.size() - 1;

          if (StringUtil.containsIgnoreCase(info.prefix, "r")) {
            boolean singleQuoteUsed = false, doubleQuoteUsed = false;
            int chunkStart = 0;
            boolean firstChunk = true;
            for (int k = 0; k < line.length(); k++) {
              if (line.charAt(k) == '\'') {
                singleQuoteUsed = true;
                if (doubleQuoteUsed) {
                  if (!firstChunk) {
                    result.append(" ");
                  }
                  result.append(info.prefix).append('\'').append(line.substring(chunkStart, k)).append('\'');
                  chunkStart = k;
                  doubleQuoteUsed = false;
                  firstChunk = false;
                }
              }
              else if (line.charAt(k) == '"') {
                doubleQuoteUsed = true;
                if (singleQuoteUsed) {
                  if (!firstChunk) {
                    result.append(" ");
                  }
                  result.append(info.prefix).append('"').append(line.substring(chunkStart, k)).append('"');
                  chunkStart = k;
                  singleQuoteUsed = false;
                  firstChunk = false;
                }
              }
            }
            if (!firstChunk) {
              result.append(" ");
            }
            if (singleQuoteUsed) {
              result.append(info.prefix).append('"').append(line.substring(chunkStart)).append('"');
            }
            else if (doubleQuoteUsed) {
              result.append(info.prefix).append('\'').append(line.substring(chunkStart)).append('\'');
            }
            else {
              result.append(info.prefix).append(info.quote).append(line.substring(chunkStart)).append(info.quote);
            }
            if (!inLastLine || lastLineExcluded) {
              result.append(" ").append(info.quote).append("\\n").append(info.quote);
            }
          }
          else {
            result.append(info.prefix);
            result.append(info.quote);
            result.append(convertToValidSubString(line, info.quote, info.isTripleQuoted));
            if (!inLastLine || lastLineExcluded) {
              result.append("\\n");
            }
            result.append(info.quote);
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

  @NotNull
  private static String convertToValidSubString(@NotNull String content, char newQuote, boolean isMultiline) {
    return isMultiline ? StringUtil.escapeChar(content, newQuote) : content;
  }

  private static boolean isTripleQuotedString(@NotNull String text) {
    final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(text);
    text = text.substring(prefixLength);
    if (text.length() < 6) {
      return false;
    }
    return (text.startsWith(TRIPLE_SINGLE_QUOTE) && text.endsWith(TRIPLE_SINGLE_QUOTE)) ||
           (text.startsWith(TRIPLE_DOUBLE_QUOTE) && text.endsWith(TRIPLE_DOUBLE_QUOTE));
  }

  @NotNull
  private static List<StringNodeInfo> extractStringNodesInfo(@NotNull PyStringLiteralExpression expression) {
    return ContainerUtil.map(expression.getStringNodes(), new Function<ASTNode, StringNodeInfo>() {
      @Override
      public StringNodeInfo fun(ASTNode node) {
        return new StringNodeInfo(node);
      }
    });
  }

  private static class StringNodeInfo {
    final ASTNode node;
    final String prefix;
    final String content;
    final char quote;
    final boolean isTripleQuoted;

    public StringNodeInfo(@NotNull ASTNode node) {
      this.node = node;
      final String nodeText = node.getText();
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(nodeText);
      prefix = nodeText.substring(0, prefixLength);
      content = PyStringLiteralExpressionImpl.getNodeTextRange(nodeText).substring(nodeText);
      quote = nodeText.charAt(prefixLength);
      isTripleQuoted = isTripleQuotedString(nodeText.substring(prefixLength));
    }
  }
}
