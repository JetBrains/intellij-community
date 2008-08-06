package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitConsoleProperties;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitResultsFormTest extends BaseRUnitTestsTestCase {
  private RTestUnitResultsForm myResultsViewer;
  private RTestUnitTestProxy myTestsRootNode;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final RTestUnitConsoleProperties consoleProperties = createConsoleProperties();
    myResultsViewer = (RTestUnitResultsForm)createResultsViewer(consoleProperties);
    myTestsRootNode = myResultsViewer.getTestsRootNode();
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
    final RTestUnitTestProxy test = createTestProxy(myTestsRootNode);

    myResultsViewer.onTestStarted(test);
    myResultsViewer.onTestFailed(test);

    assertEquals(1, myResultsViewer.getTestsCurrentCount());
  }

  public void testOnTestFinished() {
    final RTestUnitTestProxy test = createTestProxy("some_test", myTestsRootNode);

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
}
