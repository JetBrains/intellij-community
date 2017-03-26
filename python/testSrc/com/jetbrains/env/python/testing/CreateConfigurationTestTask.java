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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.TestRunnerService;
import com.jetbrains.python.testing.universalTests.PyUniversalTestConfiguration;
import com.jetbrains.python.testing.universalTests.PyUniversalTestFactory;
import com.jetbrains.python.testing.universalTests.TestTargetType;
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
class CreateConfigurationTestTask<T extends RunConfiguration> extends PyExecutionFixtureTestTask {

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
                              @NotNull final Class<T> expectedConfigurationType) {
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


        final PsiElement elementToRightClickOn = getElementToRightClickOnByFile(fileName);


        final List<ConfigurationFromContext> configurationsFromContext =
          new ConfigurationContext(elementToRightClickOn).getConfigurationsFromContext();
        Assert.assertNotNull("Producers were not able to create any configuration in " + fileName, configurationsFromContext);


        final Optional<ConfigurationFromContext> maybeConfig = configurationsFromContext.stream()
          .filter(o -> myExpectedConfigurationType.isAssignableFrom(o.getConfiguration().getClass()))
          .findFirst();
        Assert.assertTrue("No configuration of expected type created", maybeConfig.isPresent());
        RunnerAndConfigurationSettings runnerAndConfigurationSettings = maybeConfig.get().getConfigurationSettings();


        Assert.assertNotNull("Producers were not able to create any configuration in " + fileName, runnerAndConfigurationSettings);
        final RunConfiguration configuration = runnerAndConfigurationSettings.getConfiguration();
        Assert.assertNotNull("No real configuration created", configuration);
        Assert.assertThat("No name for configuration", configuration.getName(), Matchers.not(Matchers.isEmptyOrNullString()));
        Assert.assertThat("Bad configuration type in " + fileName, configuration,
                          Matchers.is(Matchers.instanceOf(myExpectedConfigurationType)));

        RunManager.getInstance(getProject()).addConfiguration(runnerAndConfigurationSettings, false);

        @SuppressWarnings("unchecked") // Checked one line above
        final T typedConfiguration = (T)configuration;
        checkConfiguration(typedConfiguration);
      }
    }), ModalityState.NON_MODAL);
  }

  /**
   * @param fileName file or folder name provided by class instantiator
   * @return element to right click on to generate test
   */
  @NotNull
  protected PsiElement getElementToRightClickOnByFile(@NotNull final String fileName) {
    final VirtualFile virtualFile = myFixture.getTempDirFixture().getFile(fileName);
    assert virtualFile != null : "Can't find " + fileName;

    // Configure context by folder in case of folder, or by element if file
    final PsiElement elementToRightClickOn;
    if (virtualFile.isDirectory()) {
      elementToRightClickOn = PsiDirectoryFactory.getInstance(getProject()).createDirectory(virtualFile);
    }
    else {
      myFixture.configureByFile(fileName);
      elementToRightClickOn = myFixture.getElementAtCaret();
    }
    return elementToRightClickOn;
  }

  protected void checkConfiguration(@NotNull final T configuration) {

  }

  static class CreateConfigurationTestAndRenameClassTask extends CreateConfigurationTestTask<PyUniversalTestConfiguration> {
    CreateConfigurationTestAndRenameClassTask(@NotNull final String testRunnerName,
                                              @NotNull final Class<? extends PyUniversalTestConfiguration> expectedConfigurationType) {
      super(testRunnerName, expectedConfigurationType, "test_class.py");
    }

    @Override
    protected void checkConfiguration(@NotNull PyUniversalTestConfiguration configuration) {
      super.checkConfiguration(configuration);
      Assert.assertThat("Wrong name generated", configuration.getName(), Matchers.containsString("TheTest"));
      Assert.assertThat("Bad target generated", configuration.getTarget().getTarget(), Matchers.endsWith("TheTest"));
      myFixture.renameElementAtCaret("FooTest");
      Assert.assertThat("Name not renamed", configuration.getName(), Matchers.containsString("FooTest"));
    }
  }

  static class CreateConfigurationTestAndRenameFolderTask
    extends CreateConfigurationTestTask<PyUniversalTestConfiguration> {
    CreateConfigurationTestAndRenameFolderTask(@Nullable final String testRunnerName,
                                               @NotNull final Class<? extends PyUniversalTestConfiguration> expectedConfigurationType) {
      super(testRunnerName, expectedConfigurationType, "folderWithTests");
    }

    @Override
    protected void checkConfiguration(@NotNull final PyUniversalTestConfiguration configuration) {
      super.checkConfiguration(configuration);
      Assert.assertThat("Wrong name generated", configuration.getName(), Matchers.containsString("folderWithTests"));
      Assert.assertThat("Bad target generated", configuration.getTarget().getTarget(), Matchers.containsString("folderWithTests"));

      final VirtualFile virtualFolder = myFixture.getTempDirFixture().getFile("folderWithTests");
      assert virtualFolder != null : "Can't find folder";
      final PsiDirectory psiFolder = PsiManager.getInstance(getProject()).findDirectory(virtualFolder);
      assert psiFolder != null : "No psi for folder found";
      myFixture.renameElement(psiFolder, "newFolder");
      Assert.assertThat("Name not renamed", configuration.getName(), Matchers.containsString("newFolder"));
      Assert.assertThat("Target not renamed", configuration.getTarget().getTarget(), Matchers.containsString("newFolder"));
    }
  }

  /**
   * Task to create configuration
   */
  abstract static class PyConfigurationCreationTask<T extends PyUniversalTestConfiguration> extends PyExecutionFixtureTestTask {
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
    protected abstract PyUniversalTestFactory<T> createFactory();

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

