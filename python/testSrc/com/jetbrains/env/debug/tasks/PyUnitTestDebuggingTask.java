// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tasks;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.env.PyTestTask;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.testing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A task for testing the drop to the debugger on a test failure capabilities.
 * Call {@link com.jetbrains.env.PyEnvTestCase#runPythonTest(PyTestTask)} with an instance of this class as an argument
 * in a test method to test the drop to the debugger related logic.
 */
public class PyUnitTestDebuggingTask extends PyCustomConfigDebuggerTask {

  private @NotNull final String myScriptName;
  private @Nullable final String myTargetName;

  public PyUnitTestDebuggingTask(@NotNull String scriptName, @Nullable String targetName) {
    this("/debug/unittests_debugging/", scriptName, targetName);
  }

  public PyUnitTestDebuggingTask(@NotNull String relativeTestDataPath, @NotNull String scriptName, @Nullable String targetName) {
    super(relativeTestDataPath);
    myScriptName = scriptName;
    myTargetName = targetName;
    setScriptName(myScriptName);
  }

  @Override
  public void before() throws Exception {
    super.before();
    PyDebuggerOptionsProvider.getInstance(getProject()).setDropIntoDebuggerOnFailedTest(true);
  }

  protected void waitForPauseOnTestFailure(String exceptionClass, String errorMessage) throws InterruptedException {
    waitForPause();
    String exceptionValue = eval("__exception__").getValue();
    PythonExceptionData exceptionData = PythonExceptionData.fromString(exceptionValue);
    Assert.assertNotNull("Can't parse exception value: " + exceptionValue, exceptionData);
    Assert.assertEquals(exceptionClass, exceptionData.getExceptionClass());
    Assert.assertEquals(errorMessage, exceptionData.getErrorMessage());
  }

  @Override
  protected AbstractPythonRunConfiguration<?> createRunConfiguration(@NotNull String sdkHome, @Nullable Sdk existingSdk) {
    ConfigurationFactory factory = getRunConfigurationFactory();
    Assert.assertNotNull("There is no test run configuration with name '" + getRunConfigurationFactoryClass().getName() + "'.", factory);
    mySettings = RunManager.getInstance(getProject()).createConfiguration(getFullTargetName() + getClass().getName()
                                                                          + "_RunConfiguration", factory);

    PyAbstractTestConfiguration runConfiguration = (PyAbstractTestConfiguration)mySettings.getConfiguration();
    PyDebugRunner runner = (PyDebugRunner)ProgramRunner.getRunner(getExecutorId(), runConfiguration);
    Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();

    runConfiguration.setSdkHome(sdkHome);
    runConfiguration.setSdk(existingSdk);
    runConfiguration.setModule(myFixture.getModule());
    runConfiguration.setWorkingDirectory(myFixture.getTempDirPath());
    runConfiguration.getTarget().setTarget(getFullTargetName());
    runConfiguration.getTarget().setTargetType(PyRunTargetVariant.PYTHON);

    Assert.assertTrue(runner.canRun(executor.getId(), runConfiguration));

    return runConfiguration;
  }

  /**
   * @return the runtime class of a factory used for test run configuration creation
   */
  protected Class<? extends PyAbstractTestFactory<?>> getRunConfigurationFactoryClass() {
    return PyUnitTestFactory.class;
  }

  private @Nullable ConfigurationFactory getRunConfigurationFactory() {
    String requiredClassName = getRunConfigurationFactoryClass().getName();
    for (ConfigurationFactory factory : PythonTestConfigurationType.getInstance().getConfigurationFactories()) {
      if (factory.getClass().getName().equals(requiredClassName)) {
        return factory;
      }
    }
    return null;
  }

  protected @NotNull String getFullTargetName() {
    return myScriptName + "." + myTargetName;
  }

  public static class PythonExceptionData {

    private final @NotNull String myExceptionClass;
    private final @NotNull String myErrorMessage;

    private PythonExceptionData(@NotNull String exceptionClass, @NotNull String errorMessage) {
      myExceptionClass = exceptionClass;
      myErrorMessage = errorMessage;
    }

    public static @Nullable PythonExceptionData fromString(@NotNull String s) {
      // Matches patterns like "(<class 'AssertionError'>, AssertionError('False is not true'), <traceback object at 0x108d49230>)".
      Pattern pattern = Pattern.compile("^\\(<(?:class|type) '(.+)'>, \\w*\\(?u?'(.+)',?\\)?.+");
      Matcher matcher = pattern.matcher(s);
      if (matcher.matches()) {
        String qualifiedExceptionName = matcher.group(1);
        String[] splittedExceptionName = qualifiedExceptionName.split("\\.");
        return new PythonExceptionData(splittedExceptionName[splittedExceptionName.length - 1], matcher.group(2));
      }
      return null;
    }

    public @NotNull String getExceptionClass() {
      return myExceptionClass;
    }

    public @NotNull String getErrorMessage() {
      return myErrorMessage;
    }
  }
}
