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

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.TestRunnerService;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;


/**
 * Task to be run by env test to check tests can create configurations.
 * It sets cursor to unit-style testcase, and creates configuration from it.
 *
 * @author Ilya.Kazakevich
 */
class CreateConfigurationTestTask extends PyExecutionFixtureTestTask {

  @Nullable
  private final String myTestRunnerName;
  @NotNull
  private final String[] myFileNames;
  @NotNull
  private final Class<? extends RunConfiguration> myExpectedConfigurationType;

  /**
   * @param testRunnerName            test runner name (to set as default to make sure producer launched)
   * @param expectedConfigurationType type configuration tha should be produced
   */
  CreateConfigurationTestTask(@NotNull final String testRunnerName,
                              @NotNull final Class<? extends RunConfiguration> expectedConfigurationType) {
    this(testRunnerName, expectedConfigurationType, "test_file.py", "test_class.py");
  }

  /**
   * @param testRunnerName            test runner name (to set as default to make sure producer launched) or null if set nothing
   * @param expectedConfigurationType type configuration tha should be produced
   * @param fileNames                 python files with caret or folders to check right click on
   */
  CreateConfigurationTestTask(@Nullable final String testRunnerName,
                              @NotNull final Class<? extends RunConfiguration> expectedConfigurationType,
                              @NotNull final String... fileNames) {
    super("/testRunner/env/createConfigurationTest/");
    myTestRunnerName = testRunnerName;
    myFileNames = fileNames.clone();
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

      for (final String fileName : myFileNames) {

        final VirtualFile virtualFile = myFixture.getTempDirFixture().getFile(fileName);
        assert virtualFile != null : "Can't find " + fileName;

        final PsiElement elementToRightClickOn;
        // Configure context by folder in case of folder, or by element if file
        if (virtualFile.isDirectory()) {
          elementToRightClickOn = PsiDirectoryFactory.getInstance(getProject()).createDirectory(virtualFile);
        }
        else {
          myFixture.configureByFile(fileName);
          elementToRightClickOn = myFixture.getElementAtCaret();
        }

        final RunnerAndConfigurationSettings runnerAndConfigurationSettings =
          new ConfigurationContext(elementToRightClickOn).getConfiguration();


        Assert.assertNotNull("Producers were not able to create any configuration in " + fileName, runnerAndConfigurationSettings);
        final RunConfiguration configuration = runnerAndConfigurationSettings.getConfiguration();
        Assert.assertNotNull("No real configuration created", configuration);
        Assert.assertThat("No name for configuration", configuration.getName(), Matchers.not(Matchers.isEmptyOrNullString()));
        Assert.assertThat("Bad configuration type in " + fileName, configuration,
                          Matchers.is(Matchers.instanceOf(myExpectedConfigurationType)));
      }
    }), ModalityState.NON_MODAL);
  }
}
