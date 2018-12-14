// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public abstract class TestNGConfigurationProducer extends AbstractJavaTestConfigurationProducer<TestNGConfiguration> implements Cloneable {
  @SuppressWarnings("unused") //used in kotlin
  public TestNGConfigurationProducer() {
    super();
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return TestNGConfigurationType.getInstance();
  }

  public static String getInvocationNumber(String str) {
    return StringUtil.trimEnd(StringUtil.trimStart(str, "["), "]");
  }
}