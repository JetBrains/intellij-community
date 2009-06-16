package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerConsoleView extends BaseTestsOutputConsoleView {
  private static final Icon OUTPUT_TAB_ICON = TestsUIUtil.loadIcon("testOuput");

  private SMTestRunnerResultsForm myResultsViewer;

  public SMTRunnerConsoleView(final TestConsoleProperties consoleProperties, final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationPerRunnerSettings) {
    this(consoleProperties, runnerSettings, configurationPerRunnerSettings, null);
  }

  public SMTRunnerConsoleView(final TestConsoleProperties consoleProperties, final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationPerRunnerSettings,
                              @Nullable final String splitterProperty) {
    super(consoleProperties);
    consoleProperties.setConsole(this);

    // Results View
    myResultsViewer = new SMTestRunnerResultsForm(consoleProperties.getConfiguration(),
                                                  consoleProperties,
                                                  runnerSettings, configurationPerRunnerSettings,
                                                  splitterProperty);

    // Console
    myResultsViewer.addTab(ExecutionBundle.message("output.tab.title"), null,
                           OUTPUT_TAB_ICON,
                           getConsole().getComponent());
    myResultsViewer.addEventsListener(new TestResultsViewer.EventsListener() {
      public void onTestNodeAdded(TestResultsViewer sender, SMTestProxy test) {
        // Do nothing
      }

      public void onTestingFinished(TestResultsViewer sender) {
        // Do nothing
      }

      public void onSelected(@Nullable final PrintableTestProxy selectedTestProxy,
                             @NotNull final TestResultsViewer viewer,
                             @NotNull final TestFrameworkRunningModel model) {
        if (selectedTestProxy == null) {
          return;
        }

        // print selected content
        SMRunnerUtil.runInEventDispatchThread(new Runnable() {
          public void run() {
            getPrinter().updateOnTestSelected(selectedTestProxy);
          }
        }, ModalityState.NON_MODAL);
      }
    });
  }

  public SMTestRunnerResultsForm getResultsViewer() {
    return myResultsViewer;
  }

  public void attachToProcess(final ProcessHandler processHandler) {
    getPrinter().setCollectOutput(false);
  }

  public JComponent getComponent() {
    return myResultsViewer.getContentPane();
  }

  public void dispose() {
    if (myResultsViewer != null) {
      Disposer.dispose(myResultsViewer);
      myResultsViewer = null;
    }

    super.dispose();
  }
}
