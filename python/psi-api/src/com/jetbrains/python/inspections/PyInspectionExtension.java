// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public abstract class PyInspectionExtension {
  public static final ExtensionPointName<PyInspectionExtension> EP_NAME = ExtensionPointName.create("Pythonid.inspectionExtension");

  public boolean ignoreUnused(PsiElement local) {
    return false;
  }

  public boolean ignoreMissingDocstring(PyDocStringOwner docStringOwner) {
    return false;
  }

  public List<String> getFunctionParametersFromUsage(PsiElement elt) {
    return null;
  }

  public boolean ignoreMethodParameters(@NotNull PyFunction function) {
    return false;
  }

  public boolean ignorePackageNameInRequirements(@NotNull PyQualifiedExpression importedExpression) {
    return false;
  }

  /**
   * Checks if unresolved reference could be ignored.
   *
   * @param node element containing reference
   * @param reference unresolved reference
   * @return true if the unresolved reference could be ignored
   * @deprecated Use {@link PyInspectionExtension#ignoreUnresolvedReference(PyElement, PsiReference, TypeEvalContext)} instead.
   * This method will be remove in 2018.2.
   */
  @Deprecated
  public boolean ignoreUnresolvedReference(@NotNull PyElement node, @NotNull PsiReference reference) {
    return ignoreUnresolvedReference(node, reference, TypeEvalContext.codeInsightFallback(node.getProject()));
  }

  /**
   * Checks if unresolved reference could be ignored.
   *
   * @param node element containing reference
   * @param reference unresolved reference
   * @return true if the unresolved reference could be ignored
   */
  public boolean ignoreUnresolvedReference(@NotNull PyElement node, @NotNull PsiReference reference, @NotNull TypeEvalContext context) {
    return false;
  }

  /**
   * Checks if unresolved member could be ignored.
   *
   * @param type    type whose member will be checked
   * @param name    member name
   * @param context type evaluation context
   * @return true if the unresolved member with the specified name could be ignored
   */
  public boolean ignoreUnresolvedMember(@NotNull PyType type, @NotNull String name, @NotNull TypeEvalContext context) {
    return false;
  }

  /**
   * Returns true if access to protected (the one started with "_") symbol should not be treated as violation.
   *
   * @param expression access expression i.e. "_foo"
   * @param context    type eval to be used
   * @return true if ignore
   */
  public boolean ignoreProtectedSymbol(@NotNull final PyReferenceExpression expression, @NotNull final TypeEvalContext context) {
    return false;
  }

  public boolean ignoreInitNewSignatures(@NotNull PyFunction original, @NotNull PyFunction complementary) {
    return false;
  }
}
