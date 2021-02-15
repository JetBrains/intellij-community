// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.theoryinpractice.testng.configuration;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractTestNGPatternConfigurationProducer extends AbstractPatternBasedConfigurationProducer<TestNGConfiguration> {
  protected AbstractTestNGPatternConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull TestNGConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    final LinkedHashSet<String> classes = new LinkedHashSet<>();
    final PsiElement element = checkPatterns(context, classes);
    if (element == null) {
      return false;
    }
    if (JavaPsiFacade.getInstance(context.getProject())
          .findClass(TestNGUtil.TEST_ANNOTATION_FQN, element.getResolveScope()) == null) {
      return false;
    }
    sourceElement.set(element);
    final TestData data = configuration.getPersistantData();
    data.setPatterns(classes);
    data.TEST_OBJECT = TestType.PATTERN.getType();
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    setupConfigurationParamName(configuration, context.getLocation());
    return true;
  }

  @Override
  protected Module findModule(TestNGConfiguration configuration, Module contextModule) {
    final Set<String> patterns = configuration.data.getPatterns();
    return findModule(configuration, contextModule, patterns);
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull TestNGConfiguration testNGConfiguration, @NotNull ConfigurationContext context) {
    if (!isApplicableTestType(testNGConfiguration.getTestType(), context)) return false;
    if (differentParamSet(testNGConfiguration, context.getLocation())) return false;
    return isConfiguredFromContext(context, testNGConfiguration.getPersistantData().getPatterns());
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return TestType.PATTERN.getType().equals(type);
  }
}