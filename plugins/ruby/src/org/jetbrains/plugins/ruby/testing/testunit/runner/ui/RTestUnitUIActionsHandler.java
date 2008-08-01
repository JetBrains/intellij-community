package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsAdapter;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitUIActionsHandler extends RTestUnitEventsAdapter {
  private TestResultsViewer myResultsViewer;
  private TestConsoleProperties myConsoleProperties;

  public RTestUnitUIActionsHandler(final TestResultsViewer resultsViewer,
                                   final TestConsoleProperties consoleProperties) {
    myResultsViewer = resultsViewer;
    myConsoleProperties = consoleProperties;
  }

  @Override
  public void onTestingFinished() {
    // select first defect at the end (my be TRACK_RUNNING_TEST was enabled and affects on the fly selection)
    final RTestUnitTestProxy testsRootNode = myResultsViewer.getTestsRootNode();

    final AbstractTestProxy testProxy;
    if (TestConsoleProperties.SELECT_FIRST_DEFECT.value(myConsoleProperties)) {
      final AbstractTestProxy firstDefect =
          Filter.DEFECTIVE_LEAF.detectIn(testsRootNode.getAllTests());
      testProxy = firstDefect != null ? firstDefect : testsRootNode;
    } else {
      testProxy = testsRootNode;
    }
    myResultsViewer.selectAndNotify(testProxy);
  }

  @Override
  public void onTestStarted(@NotNull final RTestUnitTestProxy test) {
    if (TestConsoleProperties.TRACK_RUNNING_TEST.value(myConsoleProperties)) {
      myResultsViewer.selectAndNotify(test);
    }
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
}
