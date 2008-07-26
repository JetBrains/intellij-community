package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.utils.IdeaInternalUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitConsoleView extends BaseTestsOutputConsoleView {
  private RTestUnitResultsForm myResultsForm;

  public RTestUnitConsoleView(final RTestsRunConfiguration runConfig,
                              final TestConsoleProperties consoleProperties) {
    super(consoleProperties);
    consoleProperties.setConsole(this);

    // Results View
    myResultsForm = new RTestUnitResultsForm(runConfig, consoleProperties);
    myResultsForm.addTab(RBundle.message("ruby.test.runner.ui.tabs.output.title"),
                         getConsole().getComponent());
    myResultsForm.addTestsTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        final PrintableTestProxy proxy =
            (PrintableTestProxy)myResultsForm.getTreeView().getSelectedTest();

        if (proxy == null) {
          return;
        }

        IdeaInternalUtil.runInEventDispatchThread(new Runnable() {
          public void run() {
            getPrinter().updateOnTestSelected(proxy);
          }
        }, ModalityState.NON_MODAL);
      }
    });

    //TODO[romeo] add tabs: statistics
    myResultsForm.initLogConsole();
  }

  @NotNull
  public RTestUnitResultsForm getResultsForm() {
    return myResultsForm;
  }

  public void attachToProcess(final ProcessHandler processHandler) {
    myResultsForm.attachStopLogConsoleTrackingListeners(processHandler);
  }

  public JComponent getComponent() {
    return myResultsForm.getContentPane();
  }

  public void dispose() {
    Disposer.dispose(myResultsForm);
    myResultsForm = null;

    super.dispose();
  }
}
