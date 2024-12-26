// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.model;

import com.intellij.execution.Executor;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.ui.actions.RerunFailedTestsAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TestNGConsoleProperties extends JavaAwareTestConsoleProperties<TestNGConfiguration> {

  public TestNGConsoleProperties(TestNGConfiguration config, Executor executor) {
    super("TestNG", config, executor);
  }

  @Override
  protected @NotNull GlobalSearchScope initScope() {
    final TestNGConfiguration configuration = getConfiguration(); 
    final String testObject = configuration.getPersistantData().TEST_OBJECT;
    if (TestType.CLASS.getType().equals(testObject) ||
        TestType.METHOD.getType().equals(testObject)) {
      return super.initScope();
    }
    else {
      final SourceScope sourceScope = configuration.getPersistantData().getScope().getSourceScope(configuration);
      return sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.allScope(getProject());
    }
  }

  @Override
  public void appendAdditionalActions(DefaultActionGroup actionGroup, JComponent parent, TestConsoleProperties target) {
    super.appendAdditionalActions(actionGroup, parent, target);
    actionGroup.addSeparator();
    actionGroup.add(createIncludeNonStartedInRerun(target));
    actionGroup.add(Separator.getInstance());
    actionGroup.add(createHideSuccessfulConfig(target));
  }

  @Override
  public @Nullable SMTestLocator getTestLocator() {
    return JavaTestLocator.INSTANCE;
  }

  @Override
  public @Nullable AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return new RerunFailedTestsAction(consoleView, this);
  }
}
