// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.actions.AbstractAddToTestsPatternAction;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.theoryinpractice.testng.configuration.AbstractTestNGPatternConfigurationProducer;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;
import com.theoryinpractice.testng.configuration.TestNGPatternConfigurationProducer;
import com.theoryinpractice.testng.model.TestType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class TestNGAddToTestsPatternAction extends AbstractAddToTestsPatternAction<TestNGConfiguration> {
  @Override
  protected @NotNull AbstractTestNGPatternConfigurationProducer getPatternBasedProducer() {
    return RunConfigurationProducer.getInstance(TestNGPatternConfigurationProducer.class);
  }

  @Override
  protected @NotNull ConfigurationType getConfigurationType() {
    return TestNGConfigurationType.getInstance();
  }

  @Override
  protected boolean isPatternBasedConfiguration(TestNGConfiguration configuration) {
    return TestType.PATTERN.getType().equals(configuration.getPersistantData().TEST_OBJECT);
  }

  @Override
  protected Set<String> getPatterns(TestNGConfiguration configuration) {
    return configuration.getPersistantData().getPatterns();
  }
}