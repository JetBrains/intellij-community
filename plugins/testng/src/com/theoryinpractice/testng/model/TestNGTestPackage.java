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
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class TestNGTestPackage extends TestNGTestObject {
  public TestNGTestPackage(TestNGConfiguration configuration) {
    super(configuration);
  }

  @Override
  public void fillTestObjects(Map<PsiClass, Map<PsiMethod, List<String>>> classes) throws CantRunException {
    final String packageName = myConfig.getPersistantData().getPackageName();
    PsiPackage psiPackage = ApplicationManager.getApplication().runReadAction(
      new Computable<PsiPackage>() {
        @Nullable
        public PsiPackage compute() {
          return JavaPsiFacade.getInstance(myConfig.getProject()).findPackage(packageName);
        }
      }
    );
    if (psiPackage == null) {
      throw CantRunException.packageNotFound(packageName);
    }
    else {
      TestSearchScope scope = myConfig.getPersistantData().getScope();
      //TODO we should narrow this down by module really, if that's what's specified
      SourceScope sourceScope = scope.getSourceScope(myConfig);
      TestClassFilter projectFilter =
        new TestClassFilter(sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.projectScope(myConfig.getProject()), myConfig.getProject(), true, true);
      TestClassFilter filter = projectFilter.intersectionWith(PackageScope.packageScope(psiPackage, true));
      calculateDependencies(null, classes, getSearchScope(), TestNGUtil.getAllTestClasses(filter, false));
      if (classes.size() == 0) {
        throw new CantRunException("No tests found in the package \"" + packageName + '\"');
      }
    }
  }

  @Override
  public String getGeneratedName() {
    final String packageName = myConfig.getPersistantData().getPackageName();
    return packageName.length() == 0 ? "<default>" : packageName;
  }

  @Override
  public String getActionName() {
    String s = myConfig.getName();
    if (!myConfig.isGeneratedName()) return '\"' + s + '\"';
    if (myConfig.getPersistantData().getPackageName().trim().length() > 0) {
      return "Tests in \"" + myConfig.getPersistantData().getPackageName() + '\"';
    }
    else {
      return "All Tests";
    }
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final TestData data = myConfig.getPersistantData();
    PsiPackage psiPackage = JavaPsiFacade.getInstance(myConfig.getProject()).findPackage(data.getPackageName());
    if (psiPackage == null) throw new RuntimeConfigurationException("Package '" + data.getPackageName() + "' not found");
  }

  @Override
  public boolean isConfiguredByElement(PsiElement element) {
    final String packageName = myConfig.getPersistantData().getPackageName();
    if (element instanceof PsiPackage) {
      return Comparing.strEqual(packageName, ((PsiPackage)element).getQualifiedName());
    } else if (element instanceof PsiDirectory) {
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
      return psiPackage != null && Comparing.strEqual(packageName, psiPackage.getQualifiedName());
    }
    return false;
  }
}
