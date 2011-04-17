package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyJavaTypeProvider extends PyTypeProviderBase {
  @Nullable
  public PyType getReferenceType(@NotNull final PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PsiClass) {
      return new PyJavaClassType((PsiClass) referenceTarget);
    }
    if (referenceTarget instanceof PsiPackage) {
      return new PyJavaPackageType((PsiPackage) referenceTarget, anchor == null ? null : ModuleUtil.findModuleForPsiElement(anchor));
    }
    if (referenceTarget instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) referenceTarget;
      return asPyType(method.getReturnType());
    }
    if (referenceTarget instanceof PsiField) {
      return asPyType(((PsiField)referenceTarget).getType());
    }
    return null;
  }

  @Nullable
  private static PyType asPyType(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
        return new PyJavaClassType(psiClass);
      }
    }
    return null;
  }

  public PyType getParameterType(final PyNamedParameter param, final PyFunction func, TypeEvalContext context) {
    if (!(param.getParent() instanceof PyParameterList)) return null;
    List<PyNamedParameter> params = ParamHelper.collectNamedParameters((PyParameterList) param.getParent());
    final int index = params.indexOf(param);
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
