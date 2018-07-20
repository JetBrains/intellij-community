/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

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
