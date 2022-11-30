// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.testing;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import com.jetbrains.python.testing.PyAbstractTestFactory;
import com.jetbrains.python.testing.TestRunnerService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Task to be run by env test to check unit tests can create configurations.
 * It sets cursor to unit-style testcase, and creates configuration from it.
 *
 * @author Ilya.Kazakevich
 */
public abstract class CreateConfigurationTestTask<T extends AbstractPythonTestRunConfiguration<?>> extends CreateConfigurationTask<T> {

  @Nullable
  private final String myTestRunnerName;

  /**
   * @param testRunnerName            test runner name (to set as default to make sure producer launched)
   * @param expectedConfigurationType type configuration tha should be produced
   */
  CreateConfigurationTestTask(@Nullable final String testRunnerName,
                              @NotNull final Class<T> expectedConfigurationType) {
    super(expectedConfigurationType, "/testRunner/env/createConfigurationTest/");
    myTestRunnerName = testRunnerName;
  }

  protected void markFolderAsTestRoot(@NotNull String folderName) {
    WriteAction.runAndWait(() -> {
      var manager = ModuleRootManager.getInstance(myFixture.getModule());
      var model = manager.getModifiableModel();
      var testRoot = myFixture.findFileInTempDir(folderName);
      model.getContentEntries()[0].addSourceFolder(testRoot, true);
      model.commit();
    });
  }

  @Override
  public void runTestOn(@NotNull final String sdkHome, @Nullable Sdk existingSdk) throws InvalidSdkException {
    // Set as default runner to check
    if (myTestRunnerName != null) {
      TestRunnerService.getInstance(myFixture.getModule()).setProjectConfiguration(myTestRunnerName);
    }
    super.runTestOn(sdkHome, existingSdk);
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
    public void runTestOn(@NotNull final String sdkHome, @Nullable Sdk existingSdk) {
      final T configuration =
        createFactory().createTemplateConfiguration(getProject());
      configuration.setModule(myFixture.getModule());
      configuration.setSdkHome(sdkHome);
      configuration.setSdk(existingSdk);
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
  }

  /**
   * Validates configuration.
   * Implement logic in {@link #validateConfiguration}
   */
  abstract static class PyConfigurationValidationTask<T extends PyAbstractTestConfiguration> extends PyConfigurationCreationTask<T> {
    @Override
    public void runTestOn(@NotNull final String sdkHome, @Nullable Sdk existingSdk) {
      super.runTestOn(sdkHome, existingSdk);
      validateConfiguration();
    }


    protected void validateConfiguration() {
      getConfiguration().getTarget().setTargetType(PyRunTargetVariant.PATH);
      getConfiguration().getTarget().setTarget("");

      getConfiguration().checkConfiguration();
    }
  }
}

