/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;

import java.util.List;
import java.util.Map;

public class TestNGSource extends TestNGTestMethod {
  public TestNGSource(TestNGConfiguration config) {
    super(config);
  }

  @Override
  public void fillTestObjects(Map<PsiClass, Map<PsiMethod, List<String>>> classes) throws CantRunException {}

  @Override
  public String getGeneratedName() {
    final TestData data = myConfig.getPersistantData();
    return TestngBundle.message("action.text.tests.for", StringUtil.getQualifiedName(data.getMainClassName(), data.getMethodName()));
  }

  @Override
  public String getActionName() {
    return getGeneratedName();
  }
}
