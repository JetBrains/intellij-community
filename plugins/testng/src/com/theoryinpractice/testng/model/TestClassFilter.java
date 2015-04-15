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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.util.TestNGUtil;

import java.util.Arrays;

/**
 * @author Hani Suleiman
 *         Date: Jul 21, 2005
 *         Time: 9:03:06 PM
 */
public class TestClassFilter implements ClassFilter.ClassFilterWithScope
{
    public static final String GUICE_INJECTION = "com.google.inject.Inject";
    public static final String GUICE = "org.testng.annotations.Guice";
    public static final String FACTORY_INJECTION = "org.testng.annotations.Factory";

    private final GlobalSearchScope scope;
    private final Project project;
    private final boolean includeConfig;
    private final boolean checkClassCanBeInstantiated;


    public TestClassFilter(GlobalSearchScope scope, Project project, boolean includeConfig) {
      this(scope, project, includeConfig, false);
    }

    public TestClassFilter(GlobalSearchScope scope,
                           Project project,
                           boolean includeConfig,
                           boolean checkClassCanBeInstantiated) {
        this.scope = scope;
        this.project = project;
        this.includeConfig = includeConfig;
        this.checkClassCanBeInstantiated = checkClassCanBeInstantiated;
  }

    public TestClassFilter intersectionWith(GlobalSearchScope scope) {
        return new TestClassFilter(this.scope.intersectWith(scope), project, includeConfig, checkClassCanBeInstantiated);
    }

    public boolean isAccepted(final PsiClass psiClass) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          if(!ConfigurationUtil.PUBLIC_INSTANTIATABLE_CLASS.value(psiClass)) return false;
          //PsiManager manager = PsiManager.getInstance(project);
          //if(manager.getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) return true;
          boolean hasTest = TestNGUtil.hasTest(psiClass);
          if (hasTest) {
            if (checkClassCanBeInstantiated) {
              final PsiMethod[] constructors = psiClass.getConstructors();
              if (constructors.length > 0) {
                boolean canBeInstantiated = false;
                for (PsiMethod constructor : constructors) {
                  if (constructor.getParameterList().getParametersCount() == 0) {
                    canBeInstantiated = true;
                    break;
                  }
                  if (AnnotationUtil.isAnnotated(constructor, Arrays.asList(GUICE_INJECTION, FACTORY_INJECTION), true)) {
                    canBeInstantiated = true;
                    break;
                  }
                }
                if (!canBeInstantiated && !AnnotationUtil.isAnnotated(psiClass, GUICE, false)){
                  return false;
                }
              }
            }
            return true;
          }

          return includeConfig && TestNGUtil.hasConfig(psiClass, TestNGUtil.CONFIG_ANNOTATIONS_FQN_NO_TEST_LEVEL); 
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
