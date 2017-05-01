/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.env.python.testing;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.psi.PsiElement;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.TestRunnerService;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import com.jetbrains.python.testing.PyAbstractTestFactory;
import com.jetbrains.python.testing.TestTargetType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.util.List;
import java.util.Optional;


/**
 * Task to be run by env test to check tests can create configurations.
 * It sets cursor to unit-style testcase, and creates configuration from it.
 *
 * @author Ilya.Kazakevich
 */
public abstract class CreateConfigurationTestTask<T extends AbstractPythonTestRunConfiguration<?>> extends PyExecutionFixtureTestTask {

  @Nullable
  private final String myTestRunnerName;
  @NotNull
  private final Class<T> myExpectedConfigurationType;

  /**
   * @param testRunnerName            test runner name (to set as default to make sure producer launched)
   * @param expectedConfigurationType type configuration tha should be produced
   */
  CreateConfigurationTestTask(@Nullable final String testRunnerName,
                              @NotNull final Class<T> expectedConfigurationType) {
    super("/testRunner/env/createConfigurationTest/");
    myTestRunnerName = testRunnerName;
    myExpectedConfigurationType = expectedConfigurationType;
  }

  @Override
  public void runTestOn(final String sdkHome) throws InvalidSdkException, IOException {
    // Set as default runner to check
    if (myTestRunnerName != null) {
      TestRunnerService.getInstance(myFixture.getModule()).setProjectConfiguration(myTestRunnerName);
    }

    createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_ONLY);
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {

      for (final PsiElement elementToRightClickOn : getPsiElementsToRightClickOn()) {


        @SuppressWarnings("unchecked") // Checked one line above
        final T typedConfiguration = createConfigurationByElement(elementToRightClickOn, myExpectedConfigurationType);
        checkConfiguration(typedConfiguration, elementToRightClickOn);
      }
    }), ModalityState.NON_MODAL);
  }

  /**
   * @return default (template) configuration
   */
  @NotNull
  protected T getTemplateConfiguration(@NotNull final PythonConfigurationFactoryBase factory) {
    final RunnerAndConfigurationSettingsImpl settings =
      RunManagerImpl.getInstanceImpl(myFixture.getProject()).getConfigurationTemplate(factory);
    final RunConfiguration configuration = settings.getConfiguration();
    assert myExpectedConfigurationType.isAssignableFrom(configuration.getClass()): "Wrong configuration created. Wrong factory?";
    @SuppressWarnings("unchecked") //Checked one line above
    final T typedConfig = (T)configuration;
    return typedConfig;
  }

  /**
   * Emulates right click and create configurwation
   */
  @NotNull
  public static <T extends RunConfiguration> T createConfigurationByElement(@NotNull final PsiElement elementToRightClickOn,
                                                                            @NotNull Class<T> expectedConfigurationType) {
    final List<ConfigurationFromContext> configurationsFromContext =
      new ConfigurationContext(elementToRightClickOn).getConfigurationsFromContext();
    Assert.assertNotNull("Producers were not able to create any configuration in " + elementToRightClickOn, configurationsFromContext);


    Assert.assertEquals("One and only one configuration should be produced", 1, configurationsFromContext.size());

    final ConfigurationFromContext configurationFromContext = configurationsFromContext.get(0);
    Assert.assertThat("Bad configuration type", configurationFromContext.getConfiguration(),
                      Matchers.instanceOf(expectedConfigurationType));

    final RunnerAndConfigurationSettings runnerAndConfigurationSettings = configurationFromContext.getConfigurationSettings();


    Assert.assertNotNull("Producers were not able to create any configuration in " + elementToRightClickOn, runnerAndConfigurationSettings);
    final RunConfiguration configuration = runnerAndConfigurationSettings.getConfiguration();
    Assert.assertNotNull("No real configuration created", configuration);
    Assert.assertThat("No name for configuration", configuration.getName(), Matchers.not(Matchers.isEmptyOrNullString()));
    Assert.assertThat("Bad configuration type in " + elementToRightClickOn, configuration,
                      Matchers.is(Matchers.instanceOf(expectedConfigurationType)));

    RunManager.getInstance(elementToRightClickOn.getProject()).addConfiguration(runnerAndConfigurationSettings, false);

    @SuppressWarnings("unchecked") // Checked one line above
    final T typedConfiguration = (T)configuration;
    return typedConfiguration;
  }

  @NotNull
  protected abstract List<PsiElement> getPsiElementsToRightClickOn();


  protected void checkConfiguration(@NotNull final T configuration, @NotNull final PsiElement elementToRightClickOn) {
    // Configuration already checked when created, but you can do specific checks here
  }


  /**
   * Task to create configuration
   */
  abstract static class PyConfigurationCreationTask<T extends PyAbstractTestConfiguration> extends PyExecutionFixtureTestTask {
    private volatile T myConfiguration;


    PyConfigurationCreationTask() {
      super(null);
    }

    @Override
    public void runTestOn(final String sdkHome) throws Exception {
      final T configuration =
        createFactory().createTemplateConfiguration(getProject());
      configuration.setModule(myFixture.getModule());
      configuration.setSdkHome(sdkHome);
      myConfiguration = configuration;
    }

    @NotNull
    protected abstract PyAbstractTestFactory<T> createFactory();

    @NotNull
    T getConfiguration() {
      final T configuration = myConfiguration;
      assert configuration != null : "No config created. Run runTestOn()";
      return configuration;
    }

    void checkEmptyTarget() {
      myConfiguration.getTarget().setTargetType(TestTargetType.PATH);
      myConfiguration.getTarget().setTarget("");

      myConfiguration.checkConfiguration();
    }
  }
}

