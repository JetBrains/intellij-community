package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   17.03.2010
 * Time:   20:59:15
 */
public class ConvertFormatOperatorToMethodIntention extends BaseIntentionAction {
  private static final Set<Character> FORMAT_CONVERSIONS = new HashSet<Character>();
  private static final String FORMAT_FLAGS = "#0- +";
  private static final String FORMAT_LENGTH = "hlL";

  static {
    FORMAT_CONVERSIONS.add('d');
    FORMAT_CONVERSIONS.add('i');
    FORMAT_CONVERSIONS.add('o');
    FORMAT_CONVERSIONS.add('u');
    FORMAT_CONVERSIONS.add('x');
    FORMAT_CONVERSIONS.add('X');
    FORMAT_CONVERSIONS.add('e');
    FORMAT_CONVERSIONS.add('E');
    FORMAT_CONVERSIONS.add('f');
    FORMAT_CONVERSIONS.add('F');
    FORMAT_CONVERSIONS.add('g');
    FORMAT_CONVERSIONS.add('G');
    FORMAT_CONVERSIONS.add('c');
    FORMAT_CONVERSIONS.add('r');
    FORMAT_CONVERSIONS.add('s');
  }

  private static String convertFormat(PyStringLiteralExpression stringLiteralExpression) {
    StringBuilder stringBuilder = new StringBuilder("\"");
    final String[] sections = stringLiteralExpression.getStringValue().split("%");
    for (int pos = 1; pos < sections.length; ++pos) {
      String s = sections[pos];
      int index = 0;
      char c = s.charAt(index);
      if (c == '%') {
        stringBuilder.append('%');
        break;
      }
      stringBuilder.append("{");
      final int length = s.length();

      if (c == '(') {
        index = s.indexOf(')');
        stringBuilder.append(s.substring(1, index));
        ++index;
      } else {
        stringBuilder.append(pos - 1);
      }

      StringBuilder standartFormat = new StringBuilder();
      while (FORMAT_FLAGS.indexOf(s.charAt(index)) >= 0) {
        standartFormat.append(s.charAt(index));
        ++index;
      }

      if (Character.isDigit(s.charAt(index)) || c == '*') {
        int tmp = index;
        while (index < length && Character.isDigit(s.charAt(index))) {
          ++index;
        }
        standartFormat.append(s.substring(tmp, index));
      }

      if (s.charAt(index) == '.') {
        int tmp = index;
        ++index;
        while (index < length && Character.isDigit(s.charAt(index))) {
          ++index;
        }
        standartFormat.append(s.substring(tmp, index));
      }

      if (index < length && FORMAT_LENGTH.indexOf(s.charAt(index)) != -1) {
        ++index;
        // length modifier is ignored
      }

      if (FORMAT_CONVERSIONS.contains(s.charAt(index))) {
        c = s.charAt(index);
        if (c == 'r' || c == 's') {
          stringBuilder.append("!").append(c);
        }
        else {
          if (c == 'i' || c == 'u') {
            standartFormat.append('d');
          } else {
          standartFormat.append(c);
          }
        }
        ++index;
      }

      if (standartFormat.length() != 0) {
        stringBuilder.append(":").append(standartFormat);
      }
      stringBuilder.append("}");
      
      if (s.length() >= index) {
        final String substring = s.substring(index).replace("{", "{{").replace("}", "}}");
        stringBuilder.append(StringUtil.escapeStringCharacters(substring));
      }
    }
    stringBuilder.append("\"");
    return stringBuilder.toString();
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.format.operator.to.method");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyBinaryExpression binaryExpression  =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);
    if (binaryExpression == null) {
      return false;
    }
    final VirtualFile virtualFile = binaryExpression.getContainingFile().getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    final LanguageLevel languageLevel = LanguageLevel.forFile(virtualFile);
    if (languageLevel.isOlderThan(LanguageLevel.PYTHON26)) {
      return false;
    }
    if (binaryExpression.getLeftExpression() instanceof PyStringLiteralExpression && binaryExpression.getOperator() == PyTokenTypes.PERC) {
      setText(PyBundle.message("INTN.replace.with.method"));
      return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyBinaryExpression element =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final PyExpression rightExpression = element.getRightExpression();
    if (rightExpression == null) {
      return;
    }
    String text = "";
    if (rightExpression instanceof PyParenthesizedExpression) {
      final PyExpression containedExpression = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
      if (containedExpression != null) {
        text = containedExpression.getText();
      }
    }
    else {
      text = rightExpression.getText();
    }
    String targetText = convertFormat((PyStringLiteralExpression)element.getLeftExpression()) +
                        ".format(" + text + ")";
    element.replace(elementGenerator.createExpressionFromText(targetText));
  }
}
