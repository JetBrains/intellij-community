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
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.03.2010
 * Time:   17:58:56
 */
public class PyNegateComparisonIntention extends BaseIntentionAction {
  private static final Map<PyElementType, String> comparisonStrings = new HashMap<PyElementType, String>(7);
  private static final Map<PyElementType, PyElementType> invertedComparasions = new HashMap<PyElementType, PyElementType>(7);

  static {
    comparisonStrings.put(PyTokenTypes.LT, "<");
    comparisonStrings.put(PyTokenTypes.GT, ">");
    comparisonStrings.put(PyTokenTypes.EQEQ, "==");
    comparisonStrings.put(PyTokenTypes.LE, "<=");
    comparisonStrings.put(PyTokenTypes.GE, ">=");
    comparisonStrings.put(PyTokenTypes.NE, "!=");
    comparisonStrings.put(PyTokenTypes.NE_OLD, "<>");

    invertedComparasions.put(PyTokenTypes.LT, PyTokenTypes.GE);
    invertedComparasions.put(PyTokenTypes.GT, PyTokenTypes.LE);
    invertedComparasions.put(PyTokenTypes.EQEQ, PyTokenTypes.NE);
    invertedComparasions.put(PyTokenTypes.LE, PyTokenTypes.GT);
    invertedComparasions.put(PyTokenTypes.GE, PyTokenTypes.LT);
    invertedComparasions.put(PyTokenTypes.NE, PyTokenTypes.EQEQ);
    invertedComparasions.put(PyTokenTypes.NE_OLD, PyTokenTypes.EQEQ);
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.negate.comparison");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement element = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);
    if (element == null) {
      return false;
    }

    PyElementType operator = ((PyBinaryExpression)element).getOperator();
    if (!comparisonStrings.containsKey(operator)) {
      return false;
    }
    setText(PyBundle.message("INTN.negate.$0.to.$1",
                             comparisonStrings.get(operator),
                             comparisonStrings.get(invertedComparasions.get(operator))));
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);

    PsiElement parent = element.getParent();
    while (parent instanceof PyParenthesizedExpression) {
      parent = parent.getParent();
    }
    PyBinaryExpression binaryExpression = (PyBinaryExpression)element;
    final PyElementType invertedOperator = invertedComparasions.get(binaryExpression.getOperator());
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    final PyBinaryExpression newElement = elementGenerator.createBinaryExpression(project,
                                                                                  comparisonStrings.get(invertedOperator),
                                                                                  binaryExpression.getLeftExpression(),
                                                                                  binaryExpression.getRightExpression());

    if (parent instanceof PyPrefixExpression && ((PyPrefixExpression)parent).getOperationSign() == PyTokenTypes.NOT_KEYWORD) {
      parent.replace(newElement);
    } else {
      element.replace(elementGenerator.createExpressionFromText(project, "not " + newElement.getText()));
    }
  }
}
