// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.List;


/**
 * Test task that creates run configuration
 * @author Ilya.Kazakevich
 */
public abstract class CreateConfigurationTask<T extends AbstractPythonRunConfiguration<?>> extends PyExecutionFixtureTestTask {

  @NotNull
  private final Class<T> myExpectedConfigurationType;

  /**
   * @param expectedConfigurationType type configuration tha should be produced
   */
  protected CreateConfigurationTask(@NotNull final Class<T> expectedConfigurationType,
                          @NotNull String relativeTestDataPath) {
    super(relativeTestDataPath);
    myExpectedConfigurationType = expectedConfigurationType;
  }

  @Override
  public void runTestOn(@NotNull final String sdkHome, @Nullable Sdk existingSdk) throws InvalidSdkException {
    if (!existingSdk.getHomePath().equals(sdkHome)) {
      // Creates SDK that doesn't exist yet
      createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_ONLY);
    }
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {

      for (final PsiElement elementToRightClickOn : getPsiElementsToRightClickOn()) {


        if (configurationShouldBeProducedForElement(elementToRightClickOn)) {
          // Checked one line above
          final T typedConfiguration = createConfigurationByElement(elementToRightClickOn, myExpectedConfigurationType);
          Assert.assertTrue("Should use module sdk", typedConfiguration.isUseModuleSdk());
          checkConfiguration(typedConfiguration, elementToRightClickOn);
        }
        else {
          // Any py file could be run script
          // If no test config should be produced for this element then run script should be created
          createConfigurationByElement(elementToRightClickOn, PythonRunConfiguration.class);
        }
      }
    }), ModalityState.nonModal());
  }

  protected boolean configurationShouldBeProducedForElement(@NotNull final PsiElement element) {
    return true;
  }

  /**
   * @return default (template) configuration
   */
  @NotNull
  protected T getTemplateConfiguration(@NotNull final PythonConfigurationFactoryBase factory) {
    final RunnerAndConfigurationSettingsImpl settings =
      RunManagerImpl.getInstanceImpl(myFixture.getProject()).getConfigurationTemplate(factory);
    final RunConfiguration configuration = settings.getConfiguration();
    assert myExpectedConfigurationType.isAssignableFrom(configuration.getClass()) : "Wrong configuration created. Wrong factory?";
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


    Assert.assertEquals("One and only one configuration should be produced, got: " + configurationsFromContext , 1, configurationsFromContext.size());

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

    RunManager.getInstance(elementToRightClickOn.getProject()).addConfiguration(runnerAndConfigurationSettings);

    @SuppressWarnings("unchecked") // Checked one line above
    final T typedConfiguration = (T)configuration;
    return typedConfiguration;
  }

  @NotNull
  protected abstract List<PsiElement> getPsiElementsToRightClickOn();


  protected void checkConfiguration(@NotNull final T configuration, @NotNull final PsiElement elementToRightClickOn) {
    // Configuration already checked when created, but you can do specific checks here
  }
}

