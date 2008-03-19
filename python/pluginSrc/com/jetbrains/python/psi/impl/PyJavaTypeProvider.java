package com.jetbrains.python.psi.impl;

import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

  public PyType getParameterType(final PyParameter param, final PyFunction func) {
    if (!(param.getParent() instanceof PyParameterList)) return null;
    PyParameter[] params = ((PyParameterList) param.getParent()).getParameters();
    final int index = ArrayUtil.indexOf(params, param);
    if (index < 0) return null;
    final List<PyType> superMethodParameterTypes = new ArrayList<PyType>();
    PySuperMethodsSearch.search(func).forEach(new Processor<PsiElement>() {
      public boolean process(final PsiElement psiElement) {
        if (psiElement instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)psiElement;
          final PsiParameter[] psiParameters = method.getParameterList().getParameters();
          int javaIndex = method.hasModifierProperty(PsiModifier.STATIC) ? index : index-1; // adjust for 'self' parameter
          if (javaIndex < psiParameters.length) {
            PsiType paramType = psiParameters [javaIndex].getType();
            if (paramType instanceof PsiClassType) {
              final PsiClass psiClass = ((PsiClassType)paramType).resolve();
              if (psiClass != null) {
                superMethodParameterTypes.add(new PyJavaClassType(psiClass));
              }
            }
          }
        }
        return true;
      }
    });
    if (superMethodParameterTypes.size() > 0) {
      return superMethodParameterTypes.get(0);
    }
    return null;
  }
}
