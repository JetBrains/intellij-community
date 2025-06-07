// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.testframework.AbstractInClassConfigurationProducer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.theoryinpractice.testng.model.TestType;
import org.jetbrains.annotations.NotNull;

public class TestNGInClassConfigurationProducer extends TestNGConfigurationProducer
  implements DumbAware {

  private final TestNGInClassConfigurationProducerDelegate myDelegate = new TestNGInClassConfigurationProducerDelegate();

  @Override
  public void onFirstRun(@NotNull ConfigurationFromContext configuration,
                         @NotNull ConfigurationContext fromContext,
                         @NotNull Runnable performRunnable) {
    myDelegate.onFirstRun(configuration, fromContext, performRunnable);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull TestNGConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    return myDelegate.setupConfigurationFromContext(configuration, context, sourceElement);
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return myDelegate.isApplicableTestType(type, context);
  }

  private static class TestNGInClassConfigurationProducerDelegate extends AbstractInClassConfigurationProducer<TestNGConfiguration> {
    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
      return TestNGConfigurationType.getInstance();
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull TestNGConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
      return super.setupConfigurationFromContext(configuration, context, sourceElement);
    }

    @Override
    protected boolean isApplicableTestType(String type, ConfigurationContext context) {
      return TestType.CLASS.getType().equals(type) || TestType.METHOD.getType().equals(type);
    }
  }
}
