/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 23-May-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TestNGPatternConfigurationProducer extends TestNGConfigurationProducer{

  @Override
  protected boolean setupConfigurationFromContext(TestNGConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    PsiElement[] elements = collectPatternElements(context, classes);
    if (classes.size() <= 1) return false;
    final TestData data = configuration.getPersistantData();
    data.setPatterns(classes);
    data.TEST_OBJECT = TestType.PATTERN.getType();
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    sourceElement.set(elements[0]);
    return true;
  }

  static Set<PsiMember> collectTestMembers(PsiElement[] psiElements) {
    final Set<PsiMember> foundMembers = new LinkedHashSet<PsiMember>();
    for (PsiElement psiElement : psiElements) {
      if (psiElement instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)psiElement).getClasses();
        for (PsiClass aClass : classes) {
          if (JUnitUtil.isTestClass(aClass)) {
            foundMembers.add(aClass);
          }
        }
      } else if (psiElement instanceof PsiClass) {
        if (TestNGUtil.hasTest((PsiClass)psiElement)) {
          foundMembers.add((PsiClass)psiElement);
        }
      } else if (psiElement instanceof PsiMethod) {
        if (TestNGUtil.hasTest((PsiModifierListOwner)psiElement)) {
          foundMembers.add((PsiMember)psiElement);
        }
      }
    }
    return foundMembers;
  }

  private static PsiElement[] collectPatternElements(ConfigurationContext context, LinkedHashSet<String> classes) {
    final DataContext dataContext = context.getDataContext();
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (elements != null) {
      for (PsiMember psiMember : collectTestMembers(elements)) {
        if (psiMember instanceof PsiClass) {
          classes.add(((PsiClass)psiMember).getQualifiedName());
        } else {
          classes.add(psiMember.getContainingClass().getQualifiedName() + "," + psiMember.getName());
        }
      }
      return elements;
    } else {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      if (file instanceof PsiClassOwner) {
        for (PsiMember psiMember : collectTestMembers(((PsiClassOwner)file).getClasses())) {
          classes.add(((PsiClass)psiMember).getQualifiedName());
        }
        return new PsiElement[]{file};
      }
    }
    return null;
  }

  @Override
  protected Module findModule(TestNGConfiguration configuration, Module contextModule) {
    final Set<String> patterns = configuration.data.getPatterns();
    return findModule(configuration, contextModule, patterns);
  }

  public static Module findModule(ModuleBasedConfiguration configuration, Module contextModule, Set<String> patterns) {
    return JavaExecutionUtil.findModule(contextModule, patterns, configuration.getProject(), new Condition<PsiClass>() {
      @Override
      public boolean value(PsiClass psiClass) {
        return TestNGUtil.hasTest(psiClass);
      }
    });
  }

  @Override
  public boolean isConfigurationFromContext(TestNGConfiguration testNGConfiguration, ConfigurationContext context) {
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    collectPatternElements(context, classes);
    final String type = testNGConfiguration.getPersistantData().TEST_OBJECT;
    if (Comparing.equal(type, TestType.PATTERN.getType()) &&
        Comparing.equal(classes, testNGConfiguration.getPersistantData().getPatterns())) {
      return true;
    }
    return false;
  }
}