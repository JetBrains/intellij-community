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

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.theoryinpractice.testng.model.TestNGTestObject;

public abstract class TestNGConfigurationProducer extends JavaRunConfigurationProducerBase<TestNGConfiguration> implements Cloneable {

  public TestNGConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @SuppressWarnings("unused") //used in kotlin
  public TestNGConfigurationProducer() {
    super(TestNGConfigurationType.getInstance());
  }
  
  @Override
  public boolean isConfigurationFromContext(TestNGConfiguration testNGConfiguration, ConfigurationContext context) {
    if (RunConfigurationProducer.getInstance(AbstractTestNGPatternConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }
    final RunConfiguration predefinedConfiguration = context.getOriginalConfiguration(TestNGConfigurationType.getInstance());
    final Location contextLocation = context.getLocation();
    if (contextLocation == null) {
      return false;
    }
    Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }
    final PsiElement element = location.getPsiElement();
    RunnerAndConfigurationSettings template = RunManager.getInstance(location.getProject()).getConfigurationTemplate(getConfigurationFactory());
    final Module predefinedModule = ((TestNGConfiguration)template.getConfiguration()).getConfigurationModule().getModule();
    final String vmParameters =
      predefinedConfiguration instanceof TestNGConfiguration ? ((TestNGConfiguration)predefinedConfiguration).getVMParameters() : null;
    if (vmParameters != null && !Comparing.strEqual(vmParameters, testNGConfiguration.getVMParameters())) return false;
    String paramSetName = contextLocation instanceof PsiMemberParameterizedLocation
                          ? getInvocationNumber(((PsiMemberParameterizedLocation)contextLocation).getParamSetName()) : null;
    if (paramSetName != null && !Comparing.strEqual(paramSetName, testNGConfiguration.getProgramParameters())) return false;
    TestNGTestObject testobject = TestNGTestObject.fromConfig(testNGConfiguration);
    if (testobject != null) {
      if (testobject.isConfiguredByElement(element)) {
        final Module configurationModule = testNGConfiguration.getConfigurationModule().getModule();
        if (Comparing.equal(location.getModule(), configurationModule)) return true;
        if (Comparing.equal(predefinedModule, configurationModule)) return true;
      }
    }
    return false;
  }

  public static void setupConfigurationParamName(TestNGConfiguration configuration, Location contextLocation) {
    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      final String paramSetName = ((PsiMemberParameterizedLocation)contextLocation).getParamSetName();
      if (paramSetName != null) {
        configuration.setProgramParameters(getInvocationNumber(paramSetName));
      }
    }
  }
  
  public static String getInvocationNumber(String str) {
    return StringUtil.trimEnd(StringUtil.trimStart(str, "["), "]");
  }
}