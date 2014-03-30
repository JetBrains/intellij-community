/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.triple.quoted.string");
  }

  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("INTN.triple.quoted.string");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStringLiteralExpression.class);
    if (string != null) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string) return false;
      }
      String stringText = string.getText();
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
      stringText = stringText.substring(prefixLength);
      if (stringText.length() >= 6) {
        if (stringText.startsWith("'''") && stringText.endsWith("'''") ||
              stringText.startsWith("\"\"\"") && stringText.endsWith("\"\"\"")) return true;
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStringLiteralExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (string != null) {
      final PsiElement parent = string.getParent();
      String stringText = string.getText();
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
      String prefix = stringText.substring(0, prefixLength);
      Character firstQuote = stringText.substring(prefixLength).charAt(0);

      stringText = string.getStringValue();
      List<String> subStrings = StringUtil.split(stringText, "\n", false, true);

      StringBuilder result = new StringBuilder();
      if (subStrings.size() != 1)
        result.append("(");
      boolean lastString = false;
      for (String s : subStrings) {
        result.append(prefix);
        result.append(firstQuote);
        String validSubstring = convertToValidSubString(s, firstQuote);

        if (s.endsWith("'''") || s.endsWith("\"\"\"")) {
          lastString = true;
        }
        result.append(validSubstring);
        result.append(firstQuote);
        if (!lastString)
          result.append(" ").append("\n");
      }
      if (subStrings.size() != 1)
        result.append(")");
      PyExpressionStatement e = elementGenerator.createFromText(LanguageLevel.forElement(string), PyExpressionStatement.class, result.toString());

      PyExpression expression = e.getExpression();
      if ((parent instanceof PyParenthesizedExpression || parent instanceof PyTupleExpression)
          && expression instanceof PyParenthesizedExpression)
        expression = ((PyParenthesizedExpression)expression).getContainedExpression();
      if (expression != null)
        string.replace(expression);
    }
  }

  private static String convertToValidSubString(String s, Character firstQuote) {
    String subString;
    if (s.startsWith("'''") || s.startsWith("\"\"\""))
      subString = convertToValidSubString(s.substring(3), firstQuote);
    else if (s.endsWith("'''") || s.endsWith("\"\"\"")) {
      String trimmed = s.trim();
      subString = convertToValidSubString(trimmed.substring(0, trimmed.length() - 3), firstQuote);
    }
    else {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder = StringUtil.escapeStringCharacters(s.length(), s, String.valueOf(firstQuote), true, stringBuilder);
      subString = stringBuilder.toString();
    }
    return subString;
  }
}
