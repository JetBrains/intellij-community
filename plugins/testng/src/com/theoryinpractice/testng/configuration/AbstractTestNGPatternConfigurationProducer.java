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

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;

import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractTestNGPatternConfigurationProducer extends AbstractPatternBasedConfigurationProducer<TestNGConfiguration> {
  protected AbstractTestNGPatternConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
    protected boolean isTestClass(PsiClass psiClass) {
      return TestNGUtil.hasTest(psiClass);
    }

    @Override
    protected boolean isTestMethod(boolean checkAbstract, PsiElement psiElement) {
      return psiElement instanceof PsiModifierListOwner && TestNGUtil.hasTest((PsiModifierListOwner)psiElement);
    }
  
  @Override
  protected boolean setupConfigurationFromContext(TestNGConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    final LinkedHashSet<String> classes = new LinkedHashSet<>();
    final PsiElement element = checkPatterns(context, classes);
    if (element == null) {
      return false;
    }
    sourceElement.set(element);
    final TestData data = configuration.getPersistantData();
    data.setPatterns(classes);
    data.TEST_OBJECT = TestType.PATTERN.getType();
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    TestNGConfigurationProducer.setupConfigurationParamName(configuration, context.getLocation());
    return true;
  }

  @Override
  protected Module findModule(TestNGConfiguration configuration, Module contextModule) {
    final Set<String> patterns = configuration.data.getPatterns();
    return findModule(configuration, contextModule, patterns);
  }

  @Override
  public boolean isConfigurationFromContext(TestNGConfiguration testNGConfiguration, ConfigurationContext context) {
    final String type = testNGConfiguration.getPersistantData().TEST_OBJECT;
    if (Comparing.equal(type, TestType.PATTERN.getType())) {
      return isConfiguredFromContext(context, testNGConfiguration.getPersistantData().getPatterns());
    }
    return false;
  }
}