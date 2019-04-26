// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration.testDiscovery;

import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testDiscovery.TestDiscoveryConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiMethod;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;
import com.theoryinpractice.testng.configuration.TestNGRunnableState;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

public class TestNGTestDiscoveryConfigurationProducer extends TestDiscoveryConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return TestNGConfigurationType.getInstance().getConfigurationFactories()[0];
  }

  @Override
  protected void setPosition(JavaTestConfigurationBase configuration, PsiLocation<PsiMethod> position) {
    ((TestNGConfiguration)configuration).beFromSourcePosition(position);
  }

  @Override
  protected Pair<String, String> getPosition(JavaTestConfigurationBase configuration) {
    final TestData data = ((TestNGConfiguration)configuration).getPersistantData();
    if (data.TEST_OBJECT.equals(TestType.SOURCE.getType())) {
      return Pair.create(data.getMainClassName(), data.getMethodName());
    }
    return null;
  }

  @Override
  public boolean isApplicable(@NotNull Location<PsiMethod> method) {
    //TODO
    return TestNGUtil.hasTest(method.getPsiElement());
  }

  @NotNull
  @Override
  public RunProfileState createProfile(@NotNull Location<PsiMethod>[] testMethods,
                                       Module module,
                                       RunConfiguration configuration,
                                       ExecutionEnvironment environment) {
    TestData data = ((TestNGConfiguration)configuration).getPersistantData();
    data.setPatterns(collectMethodPatterns(testMethods));
    data.TEST_OBJECT = TestType.PATTERN.type;
    return new TestNGRunnableState(environment, (TestNGConfiguration)configuration);
  }
}
