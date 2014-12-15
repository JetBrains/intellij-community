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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
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

    final PyStringLiteralExpression string =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStringLiteralExpression.class);
    if (string != null) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string) return false;
      }
      String stringText = string.getText();

      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
      final String prefix = stringText.substring(0, prefixLength);
      if (StringUtil.containsIgnoreCase(prefix, "r")) {
        return false;
      }
      stringText = stringText.substring(prefixLength);
      if (stringText.length() >= 6) {
        if (stringText.startsWith(TRIPLE_SINGLE_QUOTE) && stringText.endsWith(TRIPLE_SINGLE_QUOTE) ||
            stringText.startsWith(TRIPLE_DOUBLE_QUOTE) && stringText.endsWith(TRIPLE_DOUBLE_QUOTE)) {
          return true;
        }
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
    final PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStringLiteralExpression.class);
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (string != null) {
      final String stringText = string.getText();
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
      final String prefix = stringText.substring(0, prefixLength);
      final char firstQuote = stringText.charAt(prefixLength);

      final String stringContent = extractStringContent(string);
      List<String> lines = StringUtil.split(stringContent, "\n", true, false);
      boolean lastLineExcluded = false;
      if (lines.get(lines.size() - 1).isEmpty()) {
        lastLineExcluded = true;
        lines = lines.subList(0, lines.size() - 1);
      }

      final StringBuilder result = new StringBuilder();
      if (lines.size() != 1) {
        result.append("(");
      }
      for (int i = 0; i < lines.size(); i++) {
        final String validSubstring = convertToValidSubString(lines.get(i), firstQuote);

        final boolean isLastLine = i == lines.size() - 1;
        result.append(prefix);
        result.append(firstQuote);
        result.append(validSubstring);
        if (!isLastLine || lastLineExcluded) {
          result.append("\\n");
        }
        result.append(firstQuote);
        if (!isLastLine) {
          result.append("\n");
        }
      }
      if (lines.size() != 1) {
        result.append(")");
      }
      final PyExpressionStatement e = elementGenerator.createFromText(LanguageLevel.forElement(string), PyExpressionStatement.class, result.toString());

      PyExpression expression = e.getExpression();
      final PsiElement parent = string.getParent();
      if ((parent instanceof PyParenthesizedExpression || parent instanceof PyTupleExpression)
          && expression instanceof PyParenthesizedExpression) {
        expression = ((PyParenthesizedExpression)expression).getContainedExpression();
      }
      if (expression != null) {
        string.replace(expression);
      }
    }
  }

  @NotNull
  private static String extractStringContent(@NotNull PyStringLiteralExpression pyString) {
    final String text = pyString.getText();
    final StringBuilder result = new StringBuilder();
    for (TextRange range : pyString.getStringValueTextRanges()) {
      result.append(range.substring(text));
    }
    return result.toString();
  }

  @NotNull
  private static String convertToValidSubString(@NotNull String s, char firstQuote) {
    if (s.startsWith(TRIPLE_SINGLE_QUOTE) || s.startsWith(TRIPLE_DOUBLE_QUOTE)) {
      return convertToValidSubString(s.substring(3), firstQuote);
    }
    else if (s.endsWith(TRIPLE_SINGLE_QUOTE) || s.endsWith(TRIPLE_DOUBLE_QUOTE)) {
      return convertToValidSubString(s.substring(0, s.length() - 3), firstQuote);
    }
    else {
      return StringUtil.escapeChar(s, firstQuote);
    }
  }
}
