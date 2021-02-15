// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGTestObject;
import icons.TestngIcons;
import org.jetbrains.annotations.NotNull;

public final class TestNGConfigurationType extends SimpleConfigurationType {
  public TestNGConfigurationType() {
    super("TestNG", "TestNG", null, NotNullLazyValue.createValue(() -> TestngIcons.TestNG));
  }

  @Override
  public boolean isEditableInDumbMode() {
    return true;
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new TestNGConfiguration(project, this);
  }

  @NotNull
  @Override
  public String getTag() {
    return "testNg";
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.TestNG";
  }

  @NotNull
  public static TestNGConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(TestNGConfigurationType.class);
  }

  public boolean isConfigurationByLocation(RunConfiguration runConfiguration, Location location) {
    TestNGConfiguration config = (TestNGConfiguration)runConfiguration;
    TestData testObject = config.getPersistantData();
    if (testObject == null) {
      return false;
    }

    final PsiElement element = location.getPsiElement();
    final TestNGTestObject testNGTestObject = TestNGTestObject.fromConfig(config);
    if (testNGTestObject.isConfiguredByElement(element)) {
      final Module configurationModule = config.getConfigurationModule().getModule();
      if (Comparing.equal(location.getModule(), configurationModule)) return true;

      final Module predefinedModule =
        ((TestNGConfiguration)RunManager.getInstance(location.getProject()).getConfigurationTemplate(getConfigurationFactories()[0])
          .getConfiguration()).getConfigurationModule().getModule();
      return Comparing.equal(predefinedModule, configurationModule);
    }
    else {
      return false;
    }
  }
}
