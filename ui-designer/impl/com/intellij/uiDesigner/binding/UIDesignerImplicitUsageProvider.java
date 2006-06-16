/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public class UIDesignerImplicitUsageProvider implements ApplicationComponent, ImplicitUsageProvider {
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME.equals(method.getName()) &&
          method.getParameterList().getParameters().length == 0) {
        return true;
      }
    }
    return false;
  }

  public boolean isImplicitRead(PsiVariable element) {
    return false;
  }

  public boolean isImplicitWrite(PsiVariable element) {
    return element instanceof PsiField && FormReferenceProvider.getFormFile((PsiField)element) != null;
  }

  @NonNls
  public String getComponentName() {
    return "UIDesignerImplicitUsageProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
