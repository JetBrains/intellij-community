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
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.theoryinpractice.testng.model.TestData;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TestNGConfigurationType implements ConfigurationType
{
  public static final Icon ICON = IconLoader.getIcon("/resources/testNG.png");
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");

    private final ConfigurationFactory myFactory;

    public TestNGConfigurationType() {

        myFactory = new ConfigurationFactory(this)
        {
            @Override
            public RunConfiguration createTemplateConfiguration(Project project) {
                LOGGER.info("Create TestNG Template Configuration");
                return new TestNGConfiguration("", project, this);
            }

          @Override
          public Icon getIcon(@NotNull final RunConfiguration configuration) {
            return RunConfigurationExtension.getIcon((TestNGConfiguration)configuration, ICON);
          }
        };
    }

    public static TestNGConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(TestNGConfigurationType.class);
    }

    public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
      return null;
    }

    public boolean isConfigurationByLocation(RunConfiguration runConfiguration, Location location) {
        TestNGConfiguration config = (TestNGConfiguration) runConfiguration;
        TestData testobject = config.getPersistantData();
        if (testobject == null)
            return false;
        else {
          final PsiElement element = location.getPsiElement();
          if (testobject.isConfiguredByElement(element)) {
            final Module configurationModule = config.getConfigurationModule().getModule();
            if (Comparing.equal(location.getModule(), configurationModule)) return true;

            final Module predefinedModule =
              ((TestNGConfiguration)((RunManagerImpl)RunManagerEx.getInstanceEx(location.getProject())).getConfigurationTemplate(myFactory)
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
        return ICON;
    }

    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[] {myFactory};
    }

    @NotNull
    public String getId() {
        return "TestNG";
    }

}
