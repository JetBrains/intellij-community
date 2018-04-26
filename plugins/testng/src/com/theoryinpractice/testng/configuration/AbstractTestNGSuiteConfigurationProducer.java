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

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;

public abstract class AbstractTestNGSuiteConfigurationProducer extends TestNGConfigurationProducer {


  public AbstractTestNGSuiteConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  protected boolean setupConfigurationFromContext(TestNGConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    final PsiElement element = context.getPsiLocation();
    final PsiFile containingFile = element != null ? element.getContainingFile() : null;
    if (containingFile == null) return false;
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return false;
    if (!TestNGUtil.isTestngXML(virtualFile)) return false;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(context);
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.getPersistantData().SUITE_NAME = virtualFile.getPath();
    configuration.getPersistantData().TEST_OBJECT = TestType.SUITE.getType();
    configuration.restoreOriginalModule(originalModule);
    configuration.setGeneratedName();
    settings.setName(configuration.getName());
    sourceElement.set(containingFile);
    return true;
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return TestType.SUITE.getType().equals(type);
  }
}