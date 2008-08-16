package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitUIActionsHandler implements TestResultsViewer.EventsListener {
  private TestConsoleProperties myConsoleProperties;

  public RTestUnitUIActionsHandler(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  //@Override
  //public void onTestFinished(@NotNull final RTestUnitTestProxy test) {
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

  public void onTestNodeAdded(final TestResultsViewer sender, final RTestUnitTestProxy test) {
    if (TestConsoleProperties.TRACK_RUNNING_TEST.value(myConsoleProperties)) {
      sender.selectAndNotify(test);
    }
  }

  public void onTestingFinished(final TestResultsViewer sender) {
    // select first defect at the end (my be TRACK_RUNNING_TEST was enabled and affects on the fly selection)
    final RTestUnitTestProxy testsRootNode = sender.getTestsRootNode();

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
