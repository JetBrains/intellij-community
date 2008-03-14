package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyJavaTypeProvider implements PyTypeProvider {
  @Nullable
  public PyType getReferenceType(final PsiElement referenceTarget) {
    if (referenceTarget instanceof PsiClass) {
      return new PyJavaClassType((PsiClass) referenceTarget);
    }
    return null;
  }
}
