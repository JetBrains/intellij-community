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
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyFlipComparisonIntention extends PyBaseIntentionAction {
  private static final Map<PyElementType, String> FLIPPED_OPERATORS = new HashMap<>(7);

  static {
    FLIPPED_OPERATORS.put(PyTokenTypes.EQEQ, "==");
    FLIPPED_OPERATORS.put(PyTokenTypes.NE, "!=");
    FLIPPED_OPERATORS.put(PyTokenTypes.NE_OLD, "<>");
    FLIPPED_OPERATORS.put(PyTokenTypes.GE, "<=");
    FLIPPED_OPERATORS.put(PyTokenTypes.LE, ">=");
    FLIPPED_OPERATORS.put(PyTokenTypes.GT, "<");
    FLIPPED_OPERATORS.put(PyTokenTypes.LT, ">");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.flip.comparison");
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
      if (FLIPPED_OPERATORS.containsKey(operator)) {
        String operatorText = binaryExpression.getPsiOperator().getText();
        String flippedOperatorText = FLIPPED_OPERATORS.get(operator);
        if (flippedOperatorText.equals(operatorText)) {
          setText(PyPsiBundle.message("INTN.flip.comparison", operatorText));
        }
        else {
          setText(PyPsiBundle.message("INTN.flip.comparison.to.operator", operatorText, flippedOperatorText));
        }
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
      if (FLIPPED_OPERATORS.containsKey(binaryExpression.getOperator())) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        binaryExpression.replace(elementGenerator.createBinaryExpression(FLIPPED_OPERATORS.get(binaryExpression.getOperator()),
                                                                         binaryExpression.getRightExpression(),
                                                                         binaryExpression.getLeftExpression()));
        return;
      }
      binaryExpression = PsiTreeUtil.getParentOfType(binaryExpression, PyBinaryExpression.class);
    }
  }
}
