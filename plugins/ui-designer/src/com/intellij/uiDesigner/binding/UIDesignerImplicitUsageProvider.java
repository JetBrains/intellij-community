// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * @author yole
 */
public class UIDesignerImplicitUsageProvider implements ImplicitUsageProvider {
  @Override
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if ((AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME.equals(method.getName()) ||
           AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME.equals(method.getName()) ||
           AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName())) && method.getParameterList().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return element instanceof PsiField && FormReferenceProvider.getFormFile((PsiField)element) != null;
  }

  @Override
  public boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
    return isImplicitWrite(element);
  }

  @Override
  public boolean isClassWithCustomizedInitialization(@NotNull PsiElement element) {
    return element instanceof PsiClass && Stream.of(((PsiClass)element).getMethods()).anyMatch(this::isImplicitUsage);
  }
}
