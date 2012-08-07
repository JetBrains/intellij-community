package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyBinaryExpression extends PyQualifiedExpression, PyReferenceOwner {
  PyExpression getLeftExpression();
  @Nullable PyExpression getRightExpression();

  @Nullable
  PyElementType getOperator();

  @Nullable
  PsiElement getPsiOperator();

  boolean isOperator(String chars);

  PyExpression getOppositeExpression(PyExpression expression)
      throws IllegalArgumentException;
}
