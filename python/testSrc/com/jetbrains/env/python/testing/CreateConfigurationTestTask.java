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

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.psi.PsiElement;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.PythonTestConfigurationProducer;
import com.jetbrains.python.testing.TestRunnerService;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;


/**
 * Task to be run by env test to check tests can create configurations.
 * It sets cursor to unit-style testcase, and creates configuration from it.
 *
 * @author Ilya.Kazakevich
 */
class CreateConfigurationTestTask extends PyExecutionFixtureTestTask {

  @NotNull
  private final String myTestRunnerName;
  @NotNull
  private final Class<? extends PythonTestConfigurationProducer> myProducer;

  /**
   * @param producer       class of configuration producer to check
   * @param testRunnerName test runner name (to set as default to make sure producer launched)
   */
  CreateConfigurationTestTask(@NotNull final Class<? extends PythonTestConfigurationProducer> producer,
                              @NotNull final String testRunnerName) {
    myProducer = producer;
    myTestRunnerName = testRunnerName;
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/testRunner/env/createConfigurationTest/";
  }

  @Override
  public void runTestOn(final String sdkHome) throws InvalidSdkException, IOException {
    // Set as default runner to check
    TestRunnerService.getInstance(myFixture.getModule()).setProjectConfiguration(myTestRunnerName);

    createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_ONLY);
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      myFixture.configureByFile("test.py");
      // Should create configuration from test class
      checkConfigurationCreatedFrom(myFixture.getElementAtCaret());
      // And from file
      checkConfigurationCreatedFrom(myFixture.getElementAtCaret().getContainingFile());
    }), ModalityState.NON_MODAL);
  }


  private void checkConfigurationCreatedFrom(@NotNull final PsiElement element) {

    final PythonTestConfigurationProducer producer = createProducer();
    final ConfigurationFromContext context =
      producer.createConfigurationFromContext(new ConfigurationContext(element));
    Assert.assertNotNull(String.format("Failed to create context for %s", myTestRunnerName), context);
    Assert.assertNotNull(String.format("Configuration %s has not name", myTestRunnerName), context.getConfiguration().getName());
  }

  @NotNull
  private PythonTestConfigurationProducer createProducer() {
    try {
      return myProducer.getConstructor().newInstance();
    }
    catch (final InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      throw new AssertionError(String.format("Failed to create instance of %s", myProducer), e);
    }
  }
}
