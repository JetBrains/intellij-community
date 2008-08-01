package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitConsoleProperties;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitUIActionsHandlerTest extends BaseRUnitTestsTestCase {
  private MockTestResultsViewer myResultsViewer;
  private RTestUnitConsoleProperties myProperties;
  private RTestUnitUIActionsHandler myUIActionsHandler;
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

    myUIActionsHandler = new RTestUnitUIActionsHandler(myResultsViewer, myProperties);

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

  public void testSelectFirstDeffect() {
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myProperties, true);
    mySuite.setStarted();

    final RTestUnitTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // passed test
    final RTestUnitTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();
    myUIActionsHandler.onTestStarted(testPassed1);
    assertNull(mySelectedTestProxy);

    testPassed1.setFinished();
    myUIActionsHandler.onTestFinished(testPassed1);
    assertNull(mySelectedTestProxy);

    //failed test
    final RTestUnitTestProxy testFailed1 = createTestProxy("testFailed1", testsSuite);
    testFailed1.setStarted();
    myUIActionsHandler.onTestStarted(testFailed1);
    assertNull(mySelectedTestProxy);

    testFailed1.setTestFailed("", "");
    myUIActionsHandler.onTestFinished(testFailed1);
    assertNull(mySelectedTestProxy);

   // passed test numer 2
    mySelectedTestProxy = null;
    final RTestUnitTestProxy testPassed2 = createTestProxy("testPassed2", testsSuite);
    testPassed2.setStarted();
    myUIActionsHandler.onTestStarted(testPassed2);
    assertNull(mySelectedTestProxy);

    testPassed2.setFinished();
    myUIActionsHandler.onTestFinished(testPassed2);
    assertNull(mySelectedTestProxy);


    //failed test 2
    final RTestUnitTestProxy testFailed2 = createTestProxy("testFailed1", testsSuite);
    testFailed2.setStarted();
    myUIActionsHandler.onTestStarted(testFailed2);
    assertNull(mySelectedTestProxy);

    testFailed2.setTestFailed("", "");
    myUIActionsHandler.onTestFinished(testFailed2);
    assertNull(mySelectedTestProxy);

    // finish suite
    testsSuite.setFinished();
    assertNull(mySelectedTestProxy);

    //testing finished
    mySuite.setFinished();
    assertNull(mySelectedTestProxy);

    myUIActionsHandler.onTestingFinished();
    assertEquals(testFailed1, mySelectedTestProxy);
  }

  public void testTrackRunningTest() {
    TestConsoleProperties.TRACK_RUNNING_TEST.set(myProperties, true);
    mySuite.setStarted();

    final RTestUnitTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();
    assertNull(mySelectedTestProxy);

    // passed test
    final RTestUnitTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();
    myUIActionsHandler.onTestStarted(testPassed1);
    assertEquals(testPassed1, mySelectedTestProxy);

    testPassed1.setFinished();
    myUIActionsHandler.onTestFinished(testPassed1);
    assertEquals(testPassed1, mySelectedTestProxy);

    //failed test
    final RTestUnitTestProxy testFailed1 = createTestProxy("testFailed1", testsSuite);
    testFailed1.setStarted();
    myUIActionsHandler.onTestStarted(testFailed1);
    assertEquals(testFailed1, mySelectedTestProxy);

    testFailed1.setTestFailed("", "");
    myUIActionsHandler.onTestFinished(testFailed1);
    assertEquals(testFailed1, mySelectedTestProxy);

   // passed test numer 2
    mySelectedTestProxy = null;
    final RTestUnitTestProxy testPassed2 = createTestProxy("testPassed2", testsSuite);
    testPassed2.setStarted();
    myUIActionsHandler.onTestStarted(testPassed2);
    assertEquals(testPassed2, mySelectedTestProxy);

    testPassed2.setFinished();
    myUIActionsHandler.onTestFinished(testPassed2);
    assertEquals(testPassed2, mySelectedTestProxy);


    //failed test 2
    final RTestUnitTestProxy testFailed2 = createTestProxy("testFailed1", testsSuite);
    testFailed2.setStarted();
    myUIActionsHandler.onTestStarted(testFailed2);
    assertEquals(testFailed2, mySelectedTestProxy);
    final RTestUnitTestProxy lastSelectedTest = testFailed2;

    testFailed2.setTestFailed("", "");
    myUIActionsHandler.onTestFinished(testFailed2);
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    // finish suite
    testsSuite.setFinished();
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    // root suite finished
    mySuite.setFinished();
    assertEquals(lastSelectedTest, mySelectedTestProxy);

    //testing finished
    myUIActionsHandler.onTestingFinished();
    assertEquals(myResultsViewer.getTestsRootNode(), mySelectedTestProxy);
  }
}
