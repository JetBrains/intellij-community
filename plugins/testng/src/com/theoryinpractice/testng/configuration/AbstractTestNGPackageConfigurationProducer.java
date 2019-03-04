// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.execution.junit2.info.LocationUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;

public class AbstractTestNGPackageConfigurationProducer extends TestNGConfigurationProducer {
  @Override
  protected boolean setupConfigurationFromContext(TestNGConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    final PsiElement element = context.getPsiLocation();
    PsiPackage aPackage = JavaRuntimeConfigurationProducerBase.checkPackage(element);
    if (aPackage == null) {
      return false;
    }
    final Location location = context.getLocation();
    if (location == null) {
      return false;
    }
    if (!LocationUtil.isJarAttached(location, aPackage, TestNGUtil.TEST_ANNOTATION_FQN)) {
      return false;
    }
    final TestData data = configuration.data;
    data.PACKAGE_NAME = aPackage.getQualifiedName();
    data.TEST_OBJECT = TestType.PACKAGE.getType();
    data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
    configuration.setGeneratedName();
    sourceElement.set(aPackage);
    return true;
  }

  @Override
  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return TestType.PACKAGE.getType().equals(type);
  }
}