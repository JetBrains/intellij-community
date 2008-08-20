package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.utils.IdeaInternalUtil;

import javax.swing.*;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitConsoleView extends BaseTestsOutputConsoleView {
  private static final Icon OUTPUT_TAB_ICON = TestsUIUtil.loadIcon("testOuput");

  private TestResultsViewer myResultsViewer;

  public RTestUnitConsoleView(final TestConsoleProperties consoleProperties,
                              final TestResultsViewer resultsViewer) {
    super(consoleProperties);
    consoleProperties.setConsole(this);

    // Results View
    myResultsViewer = resultsViewer;

    // Console
    myResultsViewer.addTab(ExecutionBundle.message("output.tab.title"), OUTPUT_TAB_ICON,
                           getConsole().getComponent());
    myResultsViewer.addTestsProxySelectionListener(new TestProxyTreeSelectionListener() {
      public void onSelected(@Nullable final PrintableTestProxy selectedTestProxy) {
        if (selectedTestProxy == null) {
          return;
        }

        IdeaInternalUtil.runInEventDispatchThread(new Runnable() {
          public void run() {
            getPrinter().updateOnTestSelected(selectedTestProxy);
          }
        }, ModalityState.NON_MODAL);
      }
    });

    // init
    myResultsViewer.initLogFilesManager();
  }

  public void attachToProcess(final ProcessHandler processHandler) {
    // Process handler may be null only in JUnit test's mocks
    if (processHandler != null) {
      myResultsViewer.attachToProcess(processHandler);
    } else {
      assert ApplicationManager.getApplication().isUnitTestMode();
    }
    getPrinter().setCollectOutput(false);
  }

  public JComponent getComponent() {
    return myResultsViewer.getContentPane();
  }

  public void dispose() {
    Disposer.dispose(myResultsViewer);
    myResultsViewer = null;

    super.dispose();
  }
}
