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
 * User: anna
 * Date: 15-Aug-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.theoryinpractice.testng.model.TestData;
import org.jetbrains.annotations.NotNull;

public abstract class TestNGConfigurationProducer extends JavaRuntimeConfigurationProducerBase implements Cloneable {

  public TestNGConfigurationProducer() {
    super(TestNGConfigurationType.getInstance());
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    final Module predefinedModule =
      ((TestNGConfiguration)((RunManagerImpl)RunManagerEx.getInstanceEx(location.getProject()))
        .getConfigurationTemplate(getConfigurationFactory())
        .getConfiguration()).getConfigurationModule().getModule();
    for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
      TestNGConfiguration config = (TestNGConfiguration)existingConfiguration.getConfiguration();
      TestData testobject = config.getPersistantData();
      if (testobject != null){
        final PsiElement element = location.getPsiElement();
        if (testobject.isConfiguredByElement(element)) {
          final Module configurationModule = config.getConfigurationModule().getModule();
          if (Comparing.equal(location.getModule(), configurationModule)) return existingConfiguration;
          if(Comparing.equal(predefinedModule, configurationModule)) {
            return existingConfiguration;
          }
        }
      }
    }
    return null;
  }
}