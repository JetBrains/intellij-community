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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.util.TestNGUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestNGTestGroup extends TestNGTestObject {
  public TestNGTestGroup(TestNGConfiguration config) {
    super(config);
  }

  @Override
  public void fillTestObjects(Map<PsiClass, Map<PsiMethod, List<String>>> classes)
    throws CantRunException {
    final TestData data = myConfig.getPersistantData();
    //for a group, we include all classes
    final SourceScope sourceScope = data.getScope().getSourceScope(myConfig);
    final TestClassFilter classFilter =
      new TestClassFilter(sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.allScope(myConfig.getProject()),
                          myConfig.getProject(), true, true);
    PsiClass[] testClasses = TestNGUtil.getAllTestClasses(classFilter, false);
    if (testClasses != null) {
      for (PsiClass c : testClasses) {
        classes.put(c, new LinkedHashMap<>());
      }
    }
  }

  @Override
  public String getGeneratedName() {
    return myConfig.getPersistantData().getGroupName();
  }

  @Override
  public String getActionName() {
    return myConfig.getPersistantData().getGroupName();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    //check group exist?
  }
}
