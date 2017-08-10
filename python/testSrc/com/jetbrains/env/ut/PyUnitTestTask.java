/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.env.ut;

import com.google.common.collect.Lists;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * TODO: Move {@link com.jetbrains.python.gherkin.PyBDDEnvTestTask} to the new API and git rid of this class
 */

/**
 * Tasks to run unit test configurations.
 * You should extend it either implementing {@link #after()} and {@link #before()} or implement {@link #runTestOn(String)}
 * yourself and use {@link #runConfiguration(com.intellij.execution.configurations.ConfigurationFactory, String, com.intellij.openapi.project.Project)}
 * or {@link #runConfiguration(com.intellij.execution.RunnerAndConfigurationSettings, com.intellij.execution.configurations.RunConfiguration)} .
 * Use {@link #myDescriptor} and {@link #myConsoleView} to check output
 *
 * @author traff
 * @deprecated Consider using {@link com.jetbrains.env.PyProcessWithConsoleTestTask} instead. It has runner for python tests.
 * This class is here only because {@link com.jetbrains.python.gherkin.PyBDDEnvTestTask} uses it.
 */
@Deprecated
public abstract class PyUnitTestTask extends PyExecutionFixtureTestTask {

  protected ProcessHandler myProcessHandler;
  private boolean shouldPrintOutput = false;
  /**
   * Test root node
   */
  protected SMTestProxy.SMRootTestProxy myTestProxy;
  /**
   * Output test console
   */
  protected SMTRunnerConsoleView myConsoleView;
  /**
   * Test run descriptor
   */
  protected RunContentDescriptor myDescriptor;

  private StringBuilder myOutput;
  private boolean mySetUp = false;


  protected PyUnitTestTask(@Nullable final String relativeTestDataPath, String scriptName, String scriptParameters) {
    super(relativeTestDataPath);
    setScriptName(scriptName);
    setScriptParameters(scriptParameters);
  }

  private static void deletePycFiles(@NotNull final File directory) {
    FileUtil.processFilesRecursively(directory, file -> {
      if (file.getParentFile().getName().equals(PyNames.PYCACHE) ||
          FileUtilRt.extensionEquals(file.getName(), "pyc") ||
          FileUtilRt.extensionEquals(file.getName(), "pyo") ||
          file.getName().endsWith("$py.class")) {
        FileUtil.delete(file);
      }
      return true;
    });
  }

  @Override
  public void setUp(final String testName) throws Exception {
    if (myFixture == null) {
      super.setUp(testName);
      mySetUp = true;
    }
    deletePycFiles(new File(myFixture.getTempDirPath()));
  }

  @NotNull
  protected String output() {
    return myOutput.toString();
  }

  @Override
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        if (mySetUp) {
          if (myConsoleView != null) {
            Disposer.dispose(myConsoleView);
            myConsoleView = null;
          }
          if (myDescriptor != null) {
            Disposer.dispose(myDescriptor);
            myDescriptor = null;
          }


          PyUnitTestTask.super.tearDown();

          mySetUp = false;
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    );
  }

  @Override
  public void runTestOn(String sdkHome) throws Exception {
    final Project project = getProject();
    final ConfigurationFactory factory = PythonTestConfigurationType.getInstance().LEGACY_UNITTEST_FACTORY;
    runConfiguration(factory, sdkHome, project);
  }

  protected void runConfiguration(ConfigurationFactory factory, String sdkHome, final Project project) throws Exception {
    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration("test", factory);

    AbstractPythonLegacyTestRunConfiguration config = (AbstractPythonLegacyTestRunConfiguration)settings.getConfiguration();


    config.setSdkHome(sdkHome);
    config.setScriptName(getScriptName());
    config.setWorkingDirectory(myFixture.getTempDirPath());

    PythonSdkFlavor sdk = PythonSdkFlavor.getFlavor(sdkHome);


    if (sdk instanceof JythonSdkFlavor) {
      config.setInterpreterOptions(JythonSdkFlavor.getPythonPathCmdLineArgument(Lists.newArrayList(myFixture.getTempDirPath())));
    }
    else {
      PythonEnvUtil.addToPythonPath(config.getEnvs(), myFixture.getTempDirPath());
    }


    configure(config);

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        RunManager runManager = RunManager.getInstance(project);
        runManager.addConfiguration(settings, false);
        runManager.setSelectedConfiguration(settings);
        Assert.assertSame(settings, runManager.getSelectedConfiguration());
      }
    }.execute();

    runConfiguration(settings, config);
  }

  /**
   * Run configuration.
   *
   * @param settings settings (if have any, null otherwise)
   * @param config   configuration to run
   * @throws Exception
   */
  protected void runConfiguration(@Nullable final RunnerAndConfigurationSettings settings,
                                  @NotNull final RunConfiguration config) throws Exception {
    final ExecutionEnvironment environment;
    if (settings == null) {
      environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), config).build();
    }
    else {
      environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), settings).build();
    }
    //noinspection ConstantConditions

    Assert.assertTrue(environment.getRunner().canRun(DefaultRunExecutor.EXECUTOR_ID, config));


    before();

    final com.intellij.util.concurrency.Semaphore s = new com.intellij.util.concurrency.Semaphore();
    s.down();

    myOutput = new StringBuilder();

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        environment.getRunner().execute(environment, new ProgramRunner.Callback() {
          @Override
          public void processStarted(RunContentDescriptor descriptor) {
            myDescriptor = descriptor;
            myProcessHandler = myDescriptor.getProcessHandler();
            myProcessHandler.addProcessListener(new ProcessAdapter() {
              @Override
              public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                myOutput.append(event.getText());
              }
            });
            myConsoleView = (SMTRunnerConsoleView)descriptor.getExecutionConsole();
            myTestProxy = myConsoleView.getResultsViewer().getTestsRootNode();
            myConsoleView.getResultsViewer().addEventsListener(new TestResultsViewer.SMEventsAdapter() {
              @Override
              public void onTestingFinished(TestResultsViewer sender) {
                s.up();
              }
            });
          }
        });
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Assert.assertTrue(s.waitFor(getTestTimeout()));

    XDebuggerTestUtil.waitForSwing();

    assertFinished();

    Assert.assertTrue(output(), allTestsCount() > 0);

    after();

    disposeProcess(myProcessHandler);
  }

  protected int getTestTimeout() {
    return 60000;
  }

  protected void configure(AbstractPythonLegacyTestRunConfiguration config) {
  }

  /**
   * Searches for test by its name recursevly in test, passed as arumuent.
   *
   * @param testName test name to find
   * @param test     root test
   * @return test or null if not found
   */
  @Nullable
  private static AbstractTestProxy findTestByName(@NotNull final String testName, @NotNull final AbstractTestProxy test) {
    if (test.getName().equals(testName)) {
      return test;
    }
    for (final AbstractTestProxy testProxy : test.getChildren()) {
      final AbstractTestProxy result = findTestByName(testName, testProxy);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
  public void assertFinished() {
    Assert.assertTrue("State is " + myTestProxy.getMagnitudeInfo().getTitle() + "\n" + output(),
                      myTestProxy.wasLaunched() && !myTestProxy.wasTerminated());
  }

  public int allTestsCount() {
    return myTestProxy.collectChildren(NOT_SUIT).size();
  }

  /**
   * Gets highlighted information from test console. Some parts of output (like file links) may be highlighted, and you need to check them.
   *
   * @return pair of [[ranges], [texts]] where range is [from,to] in doc. for each region, and "text" is text extracted from this region.
   * For example assume that in document "spam eggs ham" words "ham" and "spam" are highlighted.
   * You should have 2 ranges (0, 4) and (10, 13) and 2 strings (spam and ham)
   */
  @NotNull
  public final Pair<List<Pair<Integer, Integer>>, List<String>> getHighlightedStrings() {
    final ConsoleView console = myConsoleView.getConsole();
    assert console instanceof ConsoleViewImpl : "Console has no editor!";
    final ConsoleViewImpl consoleView = (ConsoleViewImpl)console;
    final Editor editor = consoleView.getEditor();
    final List<String> resultStrings = new ArrayList<>();
    final List<Pair<Integer, Integer>> resultRanges = new ArrayList<>();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      /**
       * To fetch data from console we need to flush it first.
       * It works locally, but does not work on TC (reasons are not clear yet and need to be investigated).
       * So, we flush it explicitly to make test run on TC.
       */
      consoleView.flushDeferredText();
      for (final RangeHighlighter highlighter : editor.getMarkupModel().getAllHighlighters()) {
        if (highlighter instanceof RangeHighlighterEx) {
          final int start = ((RangeHighlighterEx)highlighter).getAffectedAreaStartOffset();
          final int end = ((RangeHighlighterEx)highlighter).getAffectedAreaEndOffset();
          resultRanges.add(Pair.create(start, end));
          resultStrings.add(editor.getDocument().getText().substring(start, end));
        }
      }
    });
    final String message = String.format("Following output is searched for hightlighed strings: %s \n", editor.getDocument().getText());
    Logger.getInstance(getClass()).warn(message);
    return Pair.create(resultRanges, resultStrings);
  }

  public static final Filter<SMTestProxy> NOT_SUIT = new Filter<SMTestProxy>() {
    @Override
    public boolean shouldAccept(SMTestProxy test) {
      return !test.isSuite();
    }
  };
}
