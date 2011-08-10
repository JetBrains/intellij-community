package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyBinaryExpression extends PyQualifiedExpression {
  PyExpression getLeftExpression();
  @Nullable PyExpression getRightExpression();

  @Nullable
  PyElementType getOperator();

  @Nullable
  PsiElement getPsiOperator();

  boolean isOperator(String chars);

  PyExpression getOppositeExpression(PyExpression expression)
      throws IllegalArgumentException;

  @Nullable
  PsiReference getReference(PyResolveContext resolveContext);
}
