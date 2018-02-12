/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGTestObject;
import icons.TestngIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TestNGConfigurationType implements ConfigurationType {

    private final ConfigurationFactory myFactory;

    public TestNGConfigurationType() {

        myFactory = new ConfigurationFactoryEx(this)
        {
            @NotNull
            @Override
            public RunConfiguration createTemplateConfiguration(Project project) {
                return new TestNGConfiguration("", project, this);
            }

          @Override
          public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
            ((ModuleBasedConfiguration)configuration).onNewConfigurationCreated();
          }
        };
    }

    public static TestNGConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(TestNGConfigurationType.class);
    }

  public boolean isConfigurationByLocation(RunConfiguration runConfiguration, Location location) {
        TestNGConfiguration config = (TestNGConfiguration) runConfiguration;
        TestData testobject = config.getPersistantData();
        if (testobject == null)
            return false;
        else {
          final PsiElement element = location.getPsiElement();
          final TestNGTestObject testNGTestObject = TestNGTestObject.fromConfig(config);
          if (testNGTestObject != null && testNGTestObject.isConfiguredByElement(element)) {
            final Module configurationModule = config.getConfigurationModule().getModule();
            if (Comparing.equal(location.getModule(), configurationModule)) return true;

            final Module predefinedModule =
              ((TestNGConfiguration)RunManager.getInstance(location.getProject()).getConfigurationTemplate(myFactory)
                .getConfiguration()).getConfigurationModule().getModule();
            return Comparing.equal(predefinedModule, configurationModule);

          }
          else {
            return false;
          }
        }
    }

    public String getDisplayName() {
        return "TestNG";
    }

    public String getConfigurationTypeDescription() {
        return "TestNG Configuration";
    }

    public Icon getIcon() {
        return TestngIcons.TestNG;
    }

    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[] {myFactory};
    }

    @NotNull
    public String getId() {
        return "TestNG";
    }

}
