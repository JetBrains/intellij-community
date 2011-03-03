package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
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
    PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStringLiteralExpression.class);
    if (string != null) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string) return false;
      }
      String stringText = string.getText();
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
      StringBuilder strBuilder = new StringBuilder();
      List<ASTNode> nodes = string.getStringNodes();
      if (nodes.size() == 1)
        strBuilder.append(string.getText());
      else {
        // There possible situation that several triple quoted string are situated together
        String firstNode = nodes.get(0).getText();
        strBuilder.append(firstNode.substring(0, firstNode.length()-3));
        for (int i = 1; i != nodes.size()-1; ++i) {
          ASTNode node = nodes.get(i);
          String text = node.getText();
          strBuilder.append(text.substring(3, text.length()-3));
        }
        strBuilder.append(nodes.get(nodes.size()-1).getText().substring(3));
      }
      String stringText = strBuilder.toString();
      String[] subStrings = stringText.split("\n");

      Character firstQuote = stringText.charAt(0);
      StringBuilder result = new StringBuilder();
      if (subStrings.length != 1)
        result.append("(");
      boolean lastString = false;
      for (String s : subStrings) {
        result.append(firstQuote);
        String validSubstring = convertToValidSubString(s, firstQuote);

        if (s.endsWith("'''") || s.endsWith("\"\"\"")) {
          lastString = true;
        }
        result.append(validSubstring);
        result.append(firstQuote);
        if (!lastString)
          result.append(" ").append(firstQuote).append("\\n").append(firstQuote).append("\n");
      }
      if (subStrings.length != 1)
        result.append(")");
      PyExpressionStatement e = elementGenerator.createFromText(LanguageLevel.forElement(string), PyExpressionStatement.class, result.toString());
      string.replace(e.getExpression());
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
      s = s.trim();
      StringBuilder stringBuilder = new StringBuilder();
      for (Character ch : s.toCharArray()) {
        if (ch == firstQuote) {
          stringBuilder.append("\\").append(firstQuote);
        }
        else {
          stringBuilder.append(ch);
        }
      }
      subString = stringBuilder.toString();
    }
    return subString;
  }
}
