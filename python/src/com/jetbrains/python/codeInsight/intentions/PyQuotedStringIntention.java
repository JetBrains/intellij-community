package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

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
      StringBuilder strBuilder = new StringBuilder();
      for (ASTNode node : string.getStringNodes())
        strBuilder.append(node.getText());
      String stringText = strBuilder.toString();//string.getText();
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
    StringBuilder stringBuilder = new StringBuilder("'");

    boolean skipNext = false;
    char[] charArr = stringText.toCharArray();
    for (int i = 0; i != charArr.length; ++i) {
      char ch = charArr[i];
      if (skipNext) {
        skipNext = false;
        continue;
      }
      if (ch == '"') {
        continue;
      //    stringBuilder.append('\'');
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

    stringBuilder.append("'");
    return stringBuilder.toString();
  }

  private static String convertSingleToDoubleQuoted(String stringText) {
    StringBuilder stringBuilder = new StringBuilder("\"");
    boolean skipNext = false;
    char[] charArr = stringText.toCharArray();
    for (int i = 0; i != charArr.length; ++i) {
      char ch = charArr[i];
      if (skipNext) {
        skipNext = false;
        continue;
      }
      if (ch == '\'') {
        continue;
        //stringBuilder.append('"');
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
    stringBuilder.append("\"");
    return stringBuilder.toString();
  }
}
