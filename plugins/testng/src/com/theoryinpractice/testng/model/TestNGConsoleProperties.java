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

import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TestNGConsoleProperties extends JavaAwareTestConsoleProperties<TestNGConfiguration> {
  private final TestNGConfiguration myConfiguration;

  public TestNGConsoleProperties(TestNGConfiguration config, Executor executor) {
    super("TestNG", config, executor);
    myConfiguration = config;
  }

  @NotNull
  @Override
  protected GlobalSearchScope initScope() {
    final String testObject = myConfiguration.getPersistantData().TEST_OBJECT;
    if (TestType.CLASS.getType().equals(testObject) ||
        TestType.METHOD.getType().equals(testObject)) {
      return super.initScope();
    }
    else {
      return myConfiguration.getPersistantData().getScope().getSourceScope(myConfiguration).getGlobalSearchScope();
    }
  }

  @Override
  protected void appendAdditionalActions(DefaultActionGroup actionGroup, ExecutionEnvironment environment, JComponent parent) {
    super.appendAdditionalActions(actionGroup, environment, parent);
    actionGroup.add(createIncludeNonStartedInRerun());
  }

  @Nullable
  @Override
  public SMTestLocator getTestLocator() {
    return JavaTestLocator.INSTANCE;
  }
}
