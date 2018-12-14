// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;

public class AbstractTestNGSuiteConfigurationProducer extends TestNGConfigurationProducer {
  @Override
  protected boolean setupConfigurationFromContext(TestNGConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    final PsiElement element = context.getPsiLocation();
    final PsiFile containingFile = element != null ? element.getContainingFile() : null;
    if (containingFile == null) return false;
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return false;
    if (!TestNGUtil.isTestngSuiteFile(virtualFile)) return false;
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