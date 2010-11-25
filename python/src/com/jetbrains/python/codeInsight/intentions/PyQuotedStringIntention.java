package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: catherine
 * Intention to convert between single-quoted and double-quoted strings 
 */
public class PyQuotedStringIntention extends BaseIntentionAction {

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.quoted.string");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStringLiteralExpression.class);
    if (string != null) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string) return false;
      }
      String stringText = string.getText();
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

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStringLiteralExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (string != null) {
      String stringText = string.getText();
      if (stringText.startsWith("'") && stringText.endsWith("'")) {
        String result = convertSingleToDoubleQuoted(stringText);
        PyStringLiteralExpression st = elementGenerator.createStringLiteralAlreadyEscaped(result);
        string.replace(st);
      }
      if (stringText.startsWith("\"") && stringText.endsWith("\"")) {
        String result = convertDoubleToSingleQuoted(stringText);
        PyStringLiteralExpression st = elementGenerator.createStringLiteralAlreadyEscaped(result);
        string.replace(st);
      }
    }
  }

  private static String convertDoubleToSingleQuoted(String stringText) {
    StringBuilder stringBuilder = new StringBuilder();
    for (char ch : stringText.toCharArray()) {
      if (ch == '"') {
        stringBuilder.append('\'');
      }
      else if (ch == '\'') {
        stringBuilder.append("\\\'");
      }
      else {
        stringBuilder.append(ch);
      }
    }
    return stringBuilder.toString();
  }

  private static String convertSingleToDoubleQuoted(String stringText) {
    StringBuilder stringBuilder = new StringBuilder();
    for (char ch : stringText.toCharArray()) {
      if (ch == '\'') {
        stringBuilder.append('"');
      }
      else if (ch == '"') {
        stringBuilder.append("\\\"");
      }
      else {
        stringBuilder.append(ch);
      }
    }
    return stringBuilder.toString();
  }
}
