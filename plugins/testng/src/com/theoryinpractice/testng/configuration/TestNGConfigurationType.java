/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 2, 2005
 * Time: 12:10:47 AM
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.diagnostic.Logger;
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
              ((TestNGConfiguration)RunManagerEx.getInstanceEx(location.getProject()).getConfigurationTemplate(myFactory)
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
