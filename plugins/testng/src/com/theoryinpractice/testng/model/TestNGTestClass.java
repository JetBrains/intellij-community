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
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.util.TestNGUtil;

import java.util.List;
import java.util.Map;

public class TestNGTestClass extends TestNGTestObject {
  public TestNGTestClass(TestNGConfiguration config) {
    super(config);
  }

  @Override
  public void fillTestObjects(Map<PsiClass, Map<PsiMethod, List<String>>> classes)
    throws CantRunException {
    final TestData data = myConfig.getPersistantData();
    //it's a class
    final PsiClass psiClass = ReadAction.compute(() -> ClassUtil
      .findPsiClass(PsiManager.getInstance(myConfig.getProject()), data.getMainClassName().replace('/', '.'), null, true,
                    getSearchScope()));
    if (psiClass == null) {
      throw new CantRunException(TestngBundle.message("dialog.message.no.tests.found.in.class", data.getMainClassName()));
    }
    if (null == ReadAction.compute(() -> psiClass.getQualifiedName())) {
      throw new CantRunException(TestngBundle.message("dialog.message.cannot.test.anonymous.or.local.class2", data.getMainClassName()));
    }
    calculateDependencies(null, classes, getSearchScope(), psiClass);
  }

  @Override
  public String getGeneratedName() {
    return JavaExecutionUtil.getPresentableClassName(myConfig.getPersistantData().getMainClassName());
  }

  @Override
  public String getActionName() {
    return JavaExecutionUtil.getShortClassName(myConfig.getPersistantData().MAIN_CLASS_NAME);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final TestData data = myConfig.getPersistantData();
    final SourceScope scope = data.getScope().getSourceScope(myConfig);
    if (scope == null) {
      throw new RuntimeConfigurationException(TestngBundle.message("testng.test.class.dialog.message.invalid.scope.specified.exception"));
    }
    final PsiManager manager = PsiManager.getInstance(myConfig.getProject());
    String testClassName = data.getMainClassName();
    FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY,
                                                () -> {
                                                  final PsiClass psiClass = ClassUtil.findPsiClass(manager, testClassName, null, true, scope.getGlobalSearchScope());
                                                  if (psiClass == null) {
                                                    throw new RuntimeConfigurationException(TestngBundle.message("testng.dialog.message.class.not.found.exception", testClassName));
                                                  }
                                                  if (!TestNGUtil.isTestNGClass(psiClass)) {
                                                    throw new RuntimeConfigurationWarning(ExecutionBundle.message("class.isnt.test.class.error.message", testClassName));
                                                  }
                                                  return true;
                                                });
    
  }

  @Override
  public boolean isConfiguredByElement(PsiElement element) {
    element = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
    if (element instanceof PsiClass) {
      return Comparing.strEqual(myConfig.getPersistantData().getMainClassName(), JavaExecutionUtil.getRuntimeQualifiedName((PsiClass) element));
    }
    return false;
  }
}
