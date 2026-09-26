// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;


public final class UIDesignerImplicitUsageProvider implements ImplicitUsageProvider {
  @Override
  public boolean isImplicitUsage(@NotNull PsiElement element) {
    if (element instanceof PsiMethod method) {
      if ((AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME.equals(method.getName()) ||
           AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME.equals(method.getName()) ||
           AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName())) && method.getParameterList().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isImplicitRead(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(@NotNull PsiElement element) {
    return element instanceof PsiField psiField && FormReferenceProvider.getFormFile(psiField) != null;
  }

  @Override
  public boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
    return isImplicitWrite(element);
  }

  @Override
  public boolean isClassWithCustomizedInitialization(@NotNull PsiElement element) {
    return element instanceof PsiClass psiClass && ContainerUtil.or(psiClass.getMethods(), this::isImplicitUsage);
  }
}
