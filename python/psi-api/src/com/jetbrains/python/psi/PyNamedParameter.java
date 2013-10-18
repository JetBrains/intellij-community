package com.jetbrains.python.psi;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a named parameter, as opposed to a tuple parameter.
 */
public interface PyNamedParameter extends PyParameter, PsiNamedElement, PsiNameIdentifierOwner, PyExpression, StubBasedPsiElement<PyNamedParameterStub> {
  boolean isPositionalContainer();

  boolean isKeywordContainer();

  /**
   * @param includeDefaultValue if true, include the default value after an " = ".
   * @return Canonical representation of parameter. Includes asterisks for *param and **param, and name.
   */
  @NotNull
  String getRepr(boolean includeDefaultValue);

  @Nullable
  PyAnnotation getAnnotation();
}

