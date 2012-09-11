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
package com.theoryinpractice.testng.model;

import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.util.TestNGUtil;

/**
 * @author Mark Derricutt
 */
public class TestListenerFilter implements ClassFilter.ClassFilterWithScope
{
  private final GlobalSearchScope scope;
  private final Project project;

  public TestListenerFilter(GlobalSearchScope scope, Project project) {
    this.scope = scope;
    this.project = project;
  }

  public boolean isAccepted(final PsiClass psiClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (!ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS.value(psiClass)) return false;

        return TestNGUtil.inheritsITestListener(psiClass);
      }
    });
  }

  public Project getProject() {
    return project;
  }

  public GlobalSearchScope getScope() {
    return scope;
  }
}