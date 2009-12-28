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
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;

public class TestNGPackageConfigurationProducer extends TestNGConfigurationProducer {
  private PsiPackage myPackage = null;

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    myPackage = checkPackage(element);
    if (myPackage == null) return null;
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    final TestNGConfiguration configuration = (TestNGConfiguration)settings.getConfiguration();
    final TestData data = configuration.data;
    data.PACKAGE_NAME = myPackage.getQualifiedName();
    data.TEST_OBJECT = TestType.PACKAGE.getType();
    data.setScope(setupPackageConfiguration(context, project, configuration, data.getScope()));
    configuration.setGeneratedName();
    RunConfigurationExtension.patchCreatedConfiguration(configuration);
    return settings;
  }

  public PsiElement getSourceElement() {
    return myPackage;
  }

  public int compareTo(final Object o) {
    return PREFERED;
  }
}