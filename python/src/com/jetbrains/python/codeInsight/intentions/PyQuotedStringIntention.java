// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 * Intention to convert between single-quoted and double-quoted strings
 */
public class PyQuotedStringIntention extends PyBaseIntentionAction {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.quoted.string");
  }

  @Override
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
      int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
      stringText = stringText.substring(prefixLength);

      if (stringText.length() >= 6) {
        if (stringText.startsWith("'''") && stringText.endsWith("'''") ||
              stringText.startsWith("\"\"\"") && stringText.endsWith("\"\"\"")) return false;
      }
      if (stringText.length() > 2) {
        if (stringText.startsWith("'") && stringText.endsWith("'")) {
          setText(PyBundle.message("INTN.quoted.string.single.to.double"));
          return true;
        }
        if (stringText.startsWith("\"") && stringText.endsWith("\"")) {
          setText(PyBundle.message("INTN.quoted.string.double.to.single"));
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStringLiteralExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (string != null) {
      final String stringText = string.getText();
      int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
      final String text = stringText.substring(prefixLength);

      if (text.startsWith("'") && text.endsWith("'")) {
        String result = convertSingleToDoubleQuoted(stringText);
        PyStringLiteralExpression st = elementGenerator.createStringLiteralAlreadyEscaped(result);
        string.replace(st);
      }
      if (text.startsWith("\"") && text.endsWith("\"")) {
        String result = convertDoubleToSingleQuoted(stringText);
        PyStringLiteralExpression st = elementGenerator.createStringLiteralAlreadyEscaped(result);
        string.replace(st);
      }
    }
  }

  private static String convertDoubleToSingleQuoted(String stringText) {
    StringBuilder stringBuilder = new StringBuilder();

    boolean skipNext = false;
    char[] charArr = stringText.toCharArray();
    for (int i = 0; i != charArr.length; ++i) {
      char ch = charArr[i];
      if (skipNext) {
        skipNext = false;
        continue;
      }
      if (ch == '"') {
        stringBuilder.append('\'');
        continue;
      }
      else if (ch == '\'') {
        stringBuilder.append("\\\'");
      }
      else if (ch == '\\' && charArr[i+1] == '\"' && !(i+2 == charArr.length)) {
        skipNext = true;
        stringBuilder.append(charArr[i+1]);
      }
      else {
        stringBuilder.append(ch);
      }
    }

    return stringBuilder.toString();
  }

  private static String convertSingleToDoubleQuoted(String stringText) {
    StringBuilder stringBuilder = new StringBuilder();
    boolean skipNext = false;
    char[] charArr = stringText.toCharArray();
    for (int i = 0; i != charArr.length; ++i) {
      char ch = charArr[i];
      if (skipNext) {
        skipNext = false;
        continue;
      }
      if (ch == '\'') {
        stringBuilder.append('"');
        continue;
      }
      else if (ch == '"') {
        stringBuilder.append("\\\"");
      }
      else if (ch == '\\' && charArr[i+1] == '\'' && !(i+2 == charArr.length)) {
        skipNext = true;
        stringBuilder.append(charArr[i+1]);
      }
      else {
        stringBuilder.append(ch);
      }
    }
    return stringBuilder.toString();
  }
}
