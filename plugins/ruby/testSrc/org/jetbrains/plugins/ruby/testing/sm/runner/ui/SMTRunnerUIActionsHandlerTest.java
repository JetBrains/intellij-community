package org.jetbrains.plugins.ruby.testing.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.sm.runner.BaseSMTRunnerTestCase;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerUIActionsHandlerTest extends BaseSMTRunnerTestCase {
  private MockTestResultsViewer myResultsViewer;
  private TestConsoleProperties myProperties;
  private SMTRunnerUIActionsHandler myUIActionsHandler;
  private AbstractTestProxy mySelectedTestProxy;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myProperties = createConsoleProperties();
    myResultsViewer = new MockTestResultsViewer(myProperties, mySuite) {
      @Override
      public void selectAndNotify(@Nullable final AbstractTestProxy proxy) {
        super.selectAndNotify(proxy);
        mySelectedTestProxy = proxy;
      }
    };

    myUIActionsHandler = new SMTRunnerUIActionsHandler(myProperties);

    TestConsoleProperties.HIDE_PASSED_TESTS.set(myProperties, false);
    TestConsoleProperties.OPEN_FAILURE_LINE.set(myProperties, false);
    TestConsoleProperties.SCROLL_TO_SOURCE.set(myProperties, false);
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, false);
    TestConsoleProperties.TRACK_RUNNING_TEST.set(myProperties, false);
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myResultsViewer);

    super.tearDown();
  }

  public void testSelectFirstDeffect_Failed() {
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // passed test
    final SMTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();
    
    //failed test
    final SMTestProxy testFailed1 = createTestProxy("testFailed1", testsSuite);
    testFailed1.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed1);
    assertNull(mySelectedTestProxy);

    testFailed1.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed1);
    assertNull(mySelectedTestProxy);

   // passed test numer 2
    mySelectedTestProxy = null;
    final SMTestProxy testPassed2 = createTestProxy("testPassed2", testsSuite);
    testPassed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPassed2);
    assertNull(mySelectedTestProxy);

    testPassed2.setFinished();
    //myUIActionsHandler.onTestFinished(testPassed2);
    assertNull(mySelectedTestProxy);


    //failed test 2
    final SMTestProxy testFailed2 = createTestProxy("testFailed1", testsSuite);
    testFailed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed2);
    assertNull(mySelectedTestProxy);

    testFailed2.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed2);
    assertNull(mySelectedTestProxy);

    // finish suite
    testsSuite.setFinished();
    assertNull(mySelectedTestProxy);

    //testing finished
    mySuite.setFinished();
    assertNull(mySelectedTestProxy);

    myUIActionsHandler.onTestingFinished(myResultsViewer);
    assertEquals(testFailed1, mySelectedTestProxy);
  }

  public void testSelectFirstDeffect_Error() {
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // passed test
    final SMTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();

    //failed test
    final SMTestProxy testError = createTestProxy("testError", testsSuite);
    testError.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testError);
    assertNull(mySelectedTestProxy);

    testError.setTestFailed("", "", true);
    //myUIActionsHandler.onTestFinished(testFailed1);
    assertNull(mySelectedTestProxy);

   // passed test numer 2
    mySelectedTestProxy = null;
    final SMTestProxy testPassed2 = createTestProxy("testPassed2", testsSuite);
    testPassed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPassed2);
    assertNull(mySelectedTestProxy);

    testPassed2.setFinished();
    //myUIActionsHandler.onTestFinished(testPassed2);
    assertNull(mySelectedTestProxy);


    //failed test
    final SMTestProxy testFailed2 = createTestProxy("testFailed1", testsSuite);
    testFailed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed2);
    assertNull(mySelectedTestProxy);

    testFailed2.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed2);
    assertNull(mySelectedTestProxy);

    // finish suite
    testsSuite.setFinished();
    assertNull(mySelectedTestProxy);

    //testing finished
    mySuite.setFinished();
    assertNull(mySelectedTestProxy);

    myUIActionsHandler.onTestingFinished(myResultsViewer);
    assertEquals(testError, mySelectedTestProxy);
  }


  public void testTrackRunningTest() {
    TestConsoleProperties.TRACK_RUNNING_TEST.set(myProperties, true);
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();
    assertNull(mySelectedTestProxy);

    // passed test
    final SMTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPassed1);
    assertEquals(testPassed1, mySelectedTestProxy);

    testPassed1.setFinished();
    //myUIActionsHandler.onTestFinished(testPassed1);
    assertEquals(testPassed1, mySelectedTestProxy);

    //failed test
    final SMTestProxy testFailed1 = createTestProxy("testFailed1", testsSuite);
    testFailed1.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed1);
    assertEquals(testFailed1, mySelectedTestProxy);

    testFailed1.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed1);
    assertEquals(testFailed1, mySelectedTestProxy);

    //error test
    final SMTestProxy testError = createTestProxy("testError", testsSuite);
    testError.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testError);
    assertEquals(testError, mySelectedTestProxy);

    testError.setTestFailed("", "", true);
    //myUIActionsHandler.onTestFinished(testError);
    assertEquals(testError, mySelectedTestProxy);

    //terminated test
    final SMTestProxy testTerminated = createTestProxy("testTerimated", testsSuite);
    testTerminated.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testTerminated);
    assertEquals(testTerminated, mySelectedTestProxy);

    testTerminated.setTerminated();
    //myUIActionsHandler.onTestFinished(testError);
    assertEquals(testTerminated, mySelectedTestProxy);

   // passed test numer 2
    mySelectedTestProxy = null;
    final SMTestProxy testPassed2 = createTestProxy("testPassed2", testsSuite);
    testPassed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testPassed2);
    assertEquals(testPassed2, mySelectedTestProxy);

    testPassed2.setFinished();
    //myUIActionsHandler.onTestFinished(testPassed2);
    assertEquals(testPassed2, mySelectedTestProxy);


    //failed test 2
    final SMTestProxy testFailed2 = createTestProxy("testFailed2", testsSuite);
    testFailed2.setStarted();
    myUIActionsHandler.onTestNodeAdded(myResultsViewer, testFailed2);
    assertEquals(testFailed2, mySelectedTestProxy);
    final SMTestProxy lastSelectedTest = testFailed2;

    testFailed2.setTestFailed("", "", false);
    //myUIActionsHandler.onTestFinished(testFailed2);
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    // finish suite
    testsSuite.setFinished();
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    // root suite finished
    mySuite.setFinished();
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    //testing finished
    myUIActionsHandler.onTestingFinished(myResultsViewer);
    assertEquals(lastSelectedTest, mySelectedTestProxy);
  }
}
