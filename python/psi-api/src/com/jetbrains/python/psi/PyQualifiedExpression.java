package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a qualified expression, that is, of "a.b.c..." sort.
 * User: dcheryasov
 * Date: Oct 18, 2008
 */
public interface PyQualifiedExpression extends PyExpression {
  @Nullable
  PyExpression getQualifier();

  /**
   * Returns the name to the right of the qualifier.
   *
   * @return the name referenced by the expression.
   */
  @Nullable
  String getReferencedName();

  /**
   * Returns the element representing the name (to the right of the qualifier).
   *
   * @return the name element.
   */
  @Nullable
  ASTNode getNameElement();

  @NotNull
  PsiPolyVariantReference getReference(PyResolveContext resolveContext);
}
