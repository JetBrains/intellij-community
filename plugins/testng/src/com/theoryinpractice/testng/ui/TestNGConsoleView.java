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
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 9, 2005
 * Time: 4:19:20 PM
 */
package com.theoryinpractice.testng.ui;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import com.theoryinpractice.testng.model.TestProxy;
import com.theoryinpractice.testng.model.TreeRootNode;
import org.testng.remote.strprotocol.TestResultMessage;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

public class TestNGConsoleView extends BaseTestsOutputConsoleView {
  private TestNGResults testNGResults;
  private TestProxy currentTest;

  private int myExceptionalMark = -1;
  private final TestNGConfiguration myConfiguration;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;

  public TestNGConsoleView(TestNGConfiguration config,
                           final RunnerSettings runnerSettings,
                           final ConfigurationPerRunnerSettings configurationPerRunnerSettings,
                           final TreeRootNode unboundOutputRoot,
                           Executor executor) {
    super(new TestNGConsoleProperties(config, executor), unboundOutputRoot);
    myConfiguration = config;
    myRunnerSettings = runnerSettings;
    myConfigurationPerRunnerSettings = configurationPerRunnerSettings;
  }

  protected TestResultsPanel createTestResultsPanel() {
    testNGResults = new TestNGResults(getConsole().getComponent(), myConfiguration, this, myRunnerSettings, myConfigurationPerRunnerSettings);
    return testNGResults;
  }

  @Override
  public void initUI() {
    super.initUI();
    final TestTreeView testTreeView = testNGResults.getTreeView();
    testTreeView.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        getPrinter().updateOnTestSelected(testTreeView.getSelectedTest());
      }
    });
  }

  @Override
  public void dispose() {
    super.dispose();
    testNGResults = null;
  }

  public TestNGResults getResultsView() {
    return testNGResults;
  }

  public void rebuildTree() {
    if (testNGResults != null) {
      testNGResults.rebuildTree();
    }
  }

  public void addTestResult(TestResultMessage result) {
    if (testNGResults != null) {
      int exceptionMark = myExceptionalMark == -1 ? 0 : myExceptionalMark;

      if (currentTest != null) {
        final String stackTrace = result.getStackTrace();
        if (stackTrace != null && stackTrace.length() > 10) {
          exceptionMark = currentTest.getCurrentSize();
          //trim useless crud from stacktrace
          currentTest.appendStacktrace(result);
        }
        final TestProxy failedToStart = testNGResults.getFailedToStart();
        if (failedToStart != null) {
          currentTest.addChild(failedToStart);
        }
      }
      testNGResults.addTestResult(result, exceptionMark);
      myExceptionalMark = -1;
    }
  }

  public void testStarted(TestResultMessage result) {
    if (testNGResults != null) {
      currentTest = testNGResults.testStarted(result);
    }
  }

  public void attachToProcess(ProcessHandler processHandler) {
  }

  public TestProxy getCurrentTest() {
    return currentTest;
  }

  public void finish() {
    currentTest = null;
  }

}
