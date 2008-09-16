package org.jetbrains.plugins.ruby.testing.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.Marker;
import org.jetbrains.plugins.ruby.testing.sm.runner.BaseSMTRunnerTestCase;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class SMTestRunnerResultsFormTest extends BaseSMTRunnerTestCase {
  private SMTestRunnerResultsForm myResultsViewer;
  private SMTestProxy myTestsRootNode;
  private TestConsoleProperties myConsoleProperties;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myConsoleProperties = createConsoleProperties();
    myResultsViewer = (SMTestRunnerResultsForm)createResultsViewer(myConsoleProperties);
    myTestsRootNode = myResultsViewer.getTestsRootNode();

    TestConsoleProperties.HIDE_PASSED_TESTS.set(myConsoleProperties, false);
    TestConsoleProperties.OPEN_FAILURE_LINE.set(myConsoleProperties, false);
    TestConsoleProperties.SCROLL_TO_SOURCE.set(myConsoleProperties, false);
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myConsoleProperties, false);
    TestConsoleProperties.TRACK_RUNNING_TEST.set(myConsoleProperties, false);
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myResultsViewer);
    super.tearDown();
  }

  public void testGetTestsRootNode() {
    assertNotNull(myTestsRootNode);

    myResultsViewer.onTestingFinished();
    assertNotNull(myResultsViewer.getTestsRootNode());
  }

  public void testTestingStarted() {
    myResultsViewer.onTestingStarted();

    assertTrue(myResultsViewer.getStartTime() > 0);
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    assertEquals(0, myResultsViewer.getTestsTotal());
  }

  public void testOnTestStarted() {
    myResultsViewer.onTestStarted(createTestProxy("some_test", myTestsRootNode));
    assertEquals(1, myResultsViewer.getTestsCurrentCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTestsCurrentCount());
  }

  public void testOnTestFailure() {
    final SMTestProxy test = createTestProxy(myTestsRootNode);

    myResultsViewer.onTestStarted(test);
    myResultsViewer.onTestFailed(test);

    assertEquals(1, myResultsViewer.getTestsCurrentCount());
  }

  public void testOnTestFinished() {
    final SMTestProxy test = createTestProxy("some_test", myTestsRootNode);

    myResultsViewer.onTestStarted(test);
    assertEquals(1, myResultsViewer.getTestsCurrentCount());

    myResultsViewer.onTestFinished(test);
    assertEquals(1, myResultsViewer.getTestsCurrentCount());
  }

  public void testOnTestsCountInSuite() {
    myResultsViewer.onTestsCountInSuite(200);

    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    assertEquals(200, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestsCountInSuite(50);
    assertEquals(250, myResultsViewer.getTestsTotal());
  }

  public void testOnTestStart_ChangeTotal() {
    myResultsViewer.onTestsCountInSuite(2);

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("some_test3", myTestsRootNode));
    assertEquals(3, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("some_test4", myTestsRootNode));
    assertEquals(4, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestsCountInSuite(2);
    myResultsViewer.onTestStarted(createTestProxy("another_test1", myTestsRootNode));
    assertEquals(6, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("another_test2", myTestsRootNode));
    assertEquals(6, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("another_test3", myTestsRootNode));
    assertEquals(7, myResultsViewer.getTestsTotal());
  }

  public void testOnFinishTesting_EndTime() {
    myResultsViewer.onTestingFinished();
    assertTrue(myResultsViewer.getEndTime() > 0);
  }

  public void testOnSuiteStarted() {
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onSuiteStarted(createSuiteProxy(myTestsRootNode));
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
  }

  public void testChangeSelectionAction() {
    final Marker onSelectedHappend = new Marker();
    final Ref<SMTestProxy> proxyRef = new Ref<SMTestProxy>();
    final Ref<Boolean> focusRequestedRef = new Ref<Boolean>();

    myResultsViewer.addChangeSelectionListener(new TestProxySelectionChangedListener() {
      public void onChangeSelection(@Nullable final SMTestProxy selectedTestProxy, final boolean requestFocus) {
        onSelectedHappend.set();
        proxyRef.set(selectedTestProxy);
        focusRequestedRef.set(requestFocus);
      }
    });

    final SMTestProxy suite = createSuiteProxy("suite", myTestsRootNode);
    final SMTestProxy test = createTestProxy("test", myTestsRootNode);
    myResultsViewer.onSuiteStarted(suite);
    myResultsViewer.onTestStarted(test);

    //On test
    myResultsViewer.selectAndNotify(test);
    myResultsViewer.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(test, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on suite
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    myResultsViewer.selectAndNotify(suite);
    myResultsViewer.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(suite, proxyRef.get());
    assertTrue(focusRequestedRef.get());
  }
}
