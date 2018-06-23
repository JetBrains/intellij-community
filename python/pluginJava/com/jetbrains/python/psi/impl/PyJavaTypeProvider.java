/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyJavaTypeProvider extends PyTypeProviderBase {
  @Override
  @Nullable
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PsiClass) {
      return Ref.create(new PyJavaClassType((PsiClass)referenceTarget, true));
    }
    if (referenceTarget instanceof PsiPackage) {
      final Module module = anchor == null ? null : ModuleUtilCore.findModuleForPsiElement(anchor);
      return Ref.create(new PyJavaPackageType((PsiPackage)referenceTarget, module));
    }
    if (referenceTarget instanceof PsiMethod) {
      return Ref.create(new PyJavaMethodType((PsiMethod)referenceTarget));
    }
    if (referenceTarget instanceof PsiField) {
      return PyTypeUtil.notNullToRef(asPyType(((PsiField)referenceTarget).getType()));
    }
    return null;
  }

  @Nullable
  public static PyType asPyType(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
        return new PyJavaClassType(psiClass, false);
      }
    }
    return null;
  }

  @Override
  public Ref<PyType> getParameterType(@NotNull final PyNamedParameter param,
                                      @NotNull final PyFunction func,
                                      @NotNull TypeEvalContext context) {
    if (!(param.getParent() instanceof PyParameterList)) return null;
    List<PyNamedParameter> params = ParamHelper.collectNamedParameters((PyParameterList) param.getParent());
    final int index = params.indexOf(param);
    if (index < 0) return null;
    final List<PyType> superMethodParameterTypes = new ArrayList<>();
    PySuperMethodsSearch.search(func, context).forEach(psiElement -> {
      if (psiElement instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)psiElement;
        final PsiParameter[] psiParameters = method.getParameterList().getParameters();
        int javaIndex = method.hasModifierProperty(PsiModifier.STATIC) ? index : index-1; // adjust for 'self' parameter
        if (javaIndex < psiParameters.length) {
          PsiType paramType = psiParameters [javaIndex].getType();
          if (paramType instanceof PsiClassType) {
            final PsiClass psiClass = ((PsiClassType)paramType).resolve();
            if (psiClass != null) {
              superMethodParameterTypes.add(new PyJavaClassType(psiClass, false));
            }
          }
        }
      }
      return true;
    });
    if (superMethodParameterTypes.size() > 0) {
      final PyType type = superMethodParameterTypes.get(0);
      if (type != null) {
        return Ref.create(type);
      }
    }
    return null;
  }
}
