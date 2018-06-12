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
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.execution.junit2.info.LocationUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.util.TestNGUtil;

public abstract class AbstractTestNGPackageConfigurationProducer extends TestNGConfigurationProducer {

  protected AbstractTestNGPackageConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

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