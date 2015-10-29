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

public class TestNGAddToTestsPatternAction extends AbstractAddToTestsPatternAction<TestNGConfiguration> {

  @Override
  @NotNull
  protected AbstractTestNGPatternConfigurationProducer getPatternBasedProducer() {
    return RunConfigurationProducer.getInstance(TestNGPatternConfigurationProducer.class);
  }

  @Override
  @NotNull
  protected ConfigurationType getConfigurationType() {
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