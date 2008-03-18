package com.jetbrains.python.psi.impl;

import com.intellij.psi.*;
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
    if (referenceTarget instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) referenceTarget;
      final PsiType type = method.getReturnType();
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass psiClass = classType.resolve();
        if (psiClass != null) {
          return new PyJavaClassType(psiClass);
        }
      }
    }
    return null;
  }
}
