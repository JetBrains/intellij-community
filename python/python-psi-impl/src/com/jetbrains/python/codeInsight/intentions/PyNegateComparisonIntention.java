// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyNegateComparisonIntention extends PyBaseIntentionAction {
  private static final Map<PyElementType, String> comparisonStrings = new HashMap<>(7);
  private static final Map<PyElementType, PyElementType> invertedComparasions = new HashMap<>(7);

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

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.negate.comparison");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    while (binaryExpression != null) {
      PyElementType operator = binaryExpression.getOperator();
      if (comparisonStrings.containsKey(operator)) {
        setText(PyPsiBundle.message("INTN.negate.$0.to.$1", comparisonStrings.get(operator),
                                    comparisonStrings.get(invertedComparasions.get(operator))));
        return true;
      }
      binaryExpression = PsiTreeUtil.getParentOfType(binaryExpression, PyBinaryExpression.class);
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    while (binaryExpression != null) {
      PyElementType operator = binaryExpression.getOperator();
      if (comparisonStrings.containsKey(operator)) {
        PsiElement parent = binaryExpression.getParent();
        while (parent instanceof PyParenthesizedExpression) {
          parent = parent.getParent();
        }

        final PyElementType invertedOperator = invertedComparasions.get(binaryExpression.getOperator());
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        final PyBinaryExpression newElement = elementGenerator
          .createBinaryExpression(comparisonStrings.get(invertedOperator), binaryExpression.getLeftExpression(),
                                  binaryExpression.getRightExpression());

        if (parent instanceof PyPrefixExpression && ((PyPrefixExpression)parent).getOperator() == PyTokenTypes.NOT_KEYWORD) {
          parent.replace(newElement);
        }
        else {
          final LanguageLevel level = LanguageLevel.forElement(file);
          binaryExpression.replace(elementGenerator.createExpressionFromText(level, "not " + newElement.getText()));
        }
        return;
      }
      binaryExpression = PsiTreeUtil.getParentOfType(binaryExpression, PyBinaryExpression.class);
    }
  }
}
