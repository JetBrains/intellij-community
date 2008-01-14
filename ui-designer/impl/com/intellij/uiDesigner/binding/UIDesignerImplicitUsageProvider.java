/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;

/**
 * @author yole
 */
public class UIDesignerImplicitUsageProvider implements ImplicitUsageProvider {
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if ((AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME.equals(method.getName()) ||
           AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME.equals(method.getName()) ||
           AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName())) && method.getParameterList().getParametersCount() == 0) {
        return true;
      }
    }
    return false;
  }

  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  public boolean isImplicitWrite(PsiElement element) {
    return element instanceof PsiField && FormReferenceProvider.getFormFile((PsiField)element) != null;
  }
}
