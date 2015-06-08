/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.theoryinpractice.testng.model;

import com.intellij.execution.CantRunException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class TestNGTestMethod extends TestNGTestObject {
  public TestNGTestMethod(TestNGConfiguration config) {
    super(config);
  }

  @Override
  public void fillTestObjects(Map<PsiClass, Map<PsiMethod, List<String>>> classes)
    throws CantRunException {
    final TestData data = myConfig.getPersistantData();
    final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(
      new Computable<PsiClass>() {
        @Nullable
        public PsiClass compute() {
          return ClassUtil.findPsiClass(PsiManager.getInstance(myConfig.getProject()), data.getMainClassName().replace('/', '.'), null, true, getSearchScope());
        }
      }
    );
    if (psiClass == null) {
      throw new CantRunException("No tests found in the class \"" + data.getMainClassName() + '\"');
    }
    if (null == ApplicationManager.getApplication().runReadAction(
      new Computable<String>() {
        @Nullable
        public String compute() {
          return psiClass.getQualifiedName();
        }
      }
    )) {
      throw new CantRunException("Cannot test anonymous or local class \"" + data.getMainClassName() + '\"');
    }
    collectTestMethods(classes, psiClass, data.getMethodName(), getSearchScope());
  }

  @Override
  public String getGeneratedName() {
    final TestData data = myConfig.getPersistantData();
    return JavaExecutionUtil.getPresentableClassName(data.getMainClassName()) + '.' + data.getMethodName();
  }

  @Override
  public String getActionName() {
    return myConfig.getPersistantData().getMethodName() + "()";
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final TestData data = myConfig.getPersistantData();
    final SourceScope scope = data.getScope().getSourceScope(myConfig);
    if (scope == null) {
      throw new RuntimeConfigurationException("Invalid scope specified");
    }
    PsiClass psiClass = JavaPsiFacade.getInstance(myConfig.getProject()).findClass(data.getMainClassName(), scope.getGlobalSearchScope());
    if (psiClass == null) throw new RuntimeConfigurationException("Class '" + data.getMainClassName() + "' not found");
    PsiMethod[] methods = psiClass.findMethodsByName(data.getMethodName(), true);
    if (methods.length == 0) {
      throw new RuntimeConfigurationException("Method '" + data.getMethodName() + "' not found");
    }
    for (PsiMethod method : methods) {
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        throw new RuntimeConfigurationException("Non public method '" + data.getMethodName() + "'specified");
      }
    }
  }

  @Override
  public boolean isConfiguredByElement(PsiElement element) {
    element = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
    if (element instanceof PsiMethod) {
      final PsiClass aClass = ((PsiMethod) element).getContainingClass();
      final TestData data = myConfig.getPersistantData();
      return aClass != null &&
             Comparing.strEqual(data.MAIN_CLASS_NAME, JavaExecutionUtil.getRuntimeQualifiedName(aClass)) &&
             Comparing.strEqual(data.METHOD_NAME, ((PsiMethod) element).getName());
    }
    return false;
  }
}
