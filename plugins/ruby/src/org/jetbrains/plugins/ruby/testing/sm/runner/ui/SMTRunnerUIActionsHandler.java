package org.jetbrains.plugins.ruby.testing.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerUIActionsHandler implements TestResultsViewer.EventsListener {
  private TestConsoleProperties myConsoleProperties;

  public SMTRunnerUIActionsHandler(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  //@Override
  //public void onTestFinished(@NotNull final SMTestProxy test) {
  //  if (!firstWasFound) {
  //    // select first defect on the fly
  //    if (test.isDefect()
  //        && TestConsoleProperties.SELECT_FIRST_DEFECT.value(myConsoleProperties)) {
  //
  //      myResultsViewer.selectAndNotify(test);
  //    }
  //    firstWasFound = true;
  //  }
  //}

  //TODO: SCROLL_TO_SOURCE

  public void onTestNodeAdded(final TestResultsViewer sender, final SMTestProxy test) {
    if (TestConsoleProperties.TRACK_RUNNING_TEST.value(myConsoleProperties)) {
      sender.selectAndNotify(test);
    }
  }

  public void onTestingFinished(final TestResultsViewer sender) {
    // select first defect at the end (my be TRACK_RUNNING_TEST was enabled and affects on the fly selection)
    final SMTestProxy testsRootNode = sender.getTestsRootNode();

    final AbstractTestProxy testProxy;
    if (TestConsoleProperties.SELECT_FIRST_DEFECT.value(myConsoleProperties)) {
      final AbstractTestProxy firstDefect =
          Filter.DEFECTIVE_LEAF.detectIn(testsRootNode.getAllTests());
      if (firstDefect != null) {
        sender.selectAndNotify(firstDefect);
      }
    }
  }
}
