package com.jetbrains.python.psi;

import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyReferenceExpression extends PyQualifiedExpression, PyReferenceOwner {
  PyReferenceExpression[] EMPTY_ARRAY = new PyReferenceExpression[0];

  /**
   * Goes through a chain of assignment statements until a non-assignment expression is encountered.
   * Starts at this, expecting it to resolve to a target of an assignment.
   * <i>Note: currently limited to non-branching definite assignments.</i>
   * @return value that is assigned to this element via a chain of definite assignments, or an empty resolve result.
   * <i>Note: will return null if the assignment chain ends in a target of a non-assignment statement such as 'for'.</i>
   *
   * @param resolveContext the resolve context
   */
  @NotNull
  QualifiedResolveResult followAssignmentsChain(PyResolveContext resolveContext);

  @Nullable
  PyQualifiedName asQualifiedName();

  @NotNull
  PsiPolyVariantReference getReference();

  @NotNull
  PsiPolyVariantReference getReference(PyResolveContext resolveContext);
}
