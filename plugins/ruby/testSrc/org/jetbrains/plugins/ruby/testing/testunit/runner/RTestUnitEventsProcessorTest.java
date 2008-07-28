package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitConsoleView;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitResultsForm;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitTestTreeView;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestResultsViewer;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitEventsProcessorTest extends BaseRUnitTestsTestCase {
  private RTestUnitConsoleView myConsole;
  private RTestUnitEventsProcessor myEventsProcessor;
  private TreeModel myTreeModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final RTestUnitConsoleProperties consoleProperties = createConsoleProperties();
    final TestResultsViewer resultsViewer = new RTestUnitResultsForm(consoleProperties.getConfiguration(), consoleProperties);

    myConsole = new RTestUnitConsoleView(consoleProperties, resultsViewer);
    myEventsProcessor = new RTestUnitEventsProcessor(resultsViewer);
    myTreeModel = ((RTestUnitResultsForm)resultsViewer).getTreeView().getModel();

    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    myEventsProcessor.onFinishTesting();
    Disposer.dispose(myConsole);

    super.tearDown();
  }

  public void testTestingStart() {
    assertTrue(myEventsProcessor.getStartTime() > 0);
    assertEquals(0, myEventsProcessor.getTestsCurrentCount());
    assertEquals(0, myEventsProcessor.getTestsTotal());

    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(0, myTreeModel.getChildCount(rootTreeNode));

    final RTestUnitNodeDescriptor nodeDescriptor =
        (RTestUnitNodeDescriptor)((DefaultMutableTreeNode)rootTreeNode).getUserObject();
    assertFalse(nodeDescriptor.expandOnDoubleClick());

    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertTrue(rootProxy.wasLaunched());
    assertTrue(rootProxy.isInProgress());
    assertTrue(rootProxy.isLeaf());

    assertEquals("[root]", rootTreeNode.toString());
  }

  public void test_OnTestsCount() {
    myEventsProcessor.onTestsCount(200);

    assertEquals(0, myEventsProcessor.getTestsCurrentCount());
    assertEquals(200, myEventsProcessor.getTestsTotal());
  }

  public void testTestAdded() {
    myEventsProcessor.onTestStart("some_test");

    assertEquals(1, myEventsProcessor.getTestsCurrentCount());

    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final RTestUnitTestProxy proxy =
        myEventsProcessor.getRunningTestsFullNameToProxy().get(fullName);

    assertNotNull(proxy);
    assertTrue(proxy.isInProgress());

    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);
    assertSameElements(rootProxy.getChildren(), proxy);


    myEventsProcessor.onTestStart("some_test2");
    assertEquals(2, myEventsProcessor.getTestsCurrentCount());
    final String fullName2 = myEventsProcessor.getFullTestName("some_test2");
    final RTestUnitTestProxy proxy2 =
        myEventsProcessor.getRunningTestsFullNameToProxy().get(fullName2);
    assertSameElements(rootProxy.getChildren(), proxy, proxy2);
  }

  public void testTestAdded_Twice() {
    myEventsProcessor.onTestStart("some_test");
    myEventsProcessor.onTestStart("some_test");

    assertEquals(1, myEventsProcessor.getTestsCurrentCount());
  }

  public void testTestAdded_ChangeTotal() {
    myEventsProcessor.onTestsCount(2);
    myEventsProcessor.onTestStart("some_test");
    assertEquals(2, myEventsProcessor.getTestsTotal());
    myEventsProcessor.onTestStart("some_test1");
    assertEquals(2, myEventsProcessor.getTestsTotal());
    myEventsProcessor.onTestStart("some_test2");
    assertEquals(3, myEventsProcessor.getTestsTotal());
    myEventsProcessor.onTestStart("some_test3");
    assertEquals(4, myEventsProcessor.getTestsTotal());
  }

  public void testTestFailed() {
    myEventsProcessor.onTestStart("some_test");
    myEventsProcessor.onTestFailure("some_test", "", "");

    assertEquals(1, myEventsProcessor.getTestsCurrentCount());

    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final RTestUnitTestProxy proxy =
        myEventsProcessor.getRunningTestsFullNameToProxy().get(fullName);

    assertTrue(proxy.isDefect());
    assertFalse(proxy.isInProgress());
  }

  public void testFailed_Twice() {
    myEventsProcessor.onTestStart("some_test");
    myEventsProcessor.onTestFailure("some_test", "", "");
    myEventsProcessor.onTestFailure("some_test", "", "");

    assertEquals(1, myEventsProcessor.getTestsCurrentCount());
    assertEquals(1, myEventsProcessor.getRunningTestsFullNameToProxy().size());
    assertEquals(1, myEventsProcessor.getFailedTestsSet().size());

  }

  public void testFinished() {
    myEventsProcessor.onTestStart("some_test");
    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final RTestUnitTestProxy proxy = myEventsProcessor.getRunningTestsFullNameToProxy().get(fullName);
    myEventsProcessor.onTestFinish("some_test");

    assertEquals(1, myEventsProcessor.getTestsCurrentCount());
    assertEquals(0, myEventsProcessor.getRunningTestsFullNameToProxy().size());
    assertEquals(0, myEventsProcessor.getFailedTestsSet().size());


    assertFalse(proxy.isDefect());
    assertFalse(proxy.isInProgress());

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);
    assertSameElements(rootProxy.getChildren(), proxy);
  }

  //TODO[romeo] catch assertion
  //public void testFinished_Twice() {
  //  myEventsProcessor.onTestStart("some_test");
  //  myEventsProcessor.onTestFinish("some_test");
  //  myEventsProcessor.onTestFinish("some_test");
  //
  //  assertEquals(1, myEventsProcessor.getTestsCurrentCount());
  //  assertEquals(0, myEventsProcessor.getRunningTestsFullNameToProxy().size());
  //  assertEquals(0, myEventsProcessor.getFailedTestsSet().size());
  //
  //}

  public void testTestingFinished_EmptySuite() {
    myEventsProcessor.onFinishTesting();

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(0, myTreeModel.getChildCount(rootTreeNode));
    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertFalse(rootProxy.isInProgress());
    assertTrue(rootProxy.isDefect());
  }

  public void testTestingFinished_EndTime() {
    myEventsProcessor.onFinishTesting();
    assertTrue(myEventsProcessor.getEndTime() > 0);
  }

  public void testTestingFinished_WithDefect() {
    myEventsProcessor.onTestStart("test");
    myEventsProcessor.onTestFailure("test", "", "");
    myEventsProcessor.onTestFinish("test");
    myEventsProcessor.onFinishTesting();

    assertTrue(myEventsProcessor.getEndTime() > 0);

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertFalse(rootProxy.isInProgress());
    assertTrue(rootProxy.isDefect());
  }

  public void testTestingFinished_twice() {
    myEventsProcessor.onFinishTesting();

    final long time = myEventsProcessor.getEndTime();
    myEventsProcessor.onFinishTesting();
    assertEquals(time, myEventsProcessor.getEndTime());
  }

  public void testSuiteStarted() {
    assertEquals(0, myEventsProcessor.getTestsCurrentCount());
    myEventsProcessor.onTestSuiteStart("suite1");
    assertEquals(0, myEventsProcessor.getTestsCurrentCount());

    //lets check that new tests have right parent
    myEventsProcessor.onTestStart("test1");
    final RTestUnitTestProxy test1 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test1"));
    assertEquals("suite1", test1.getParent().getName());

    //lets check that new suits have righ parent
    myEventsProcessor.onTestSuiteStart("suite2");
    myEventsProcessor.onTestSuiteStart("suite3");
    myEventsProcessor.onTestStart("test2");
    final RTestUnitTestProxy test2 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test2"));
    assertEquals("suite3", test2.getParent().getName());
    assertEquals("suite2", test2.getParent().getParent().getName());

    myEventsProcessor.onTestFinish("test2");

    //check that after finishing suite (suite3), current will be parent of finished suite (i.e. suite2)
    myEventsProcessor.onTestSuiteFinish("suite3");
    myEventsProcessor.onTestStart("test3");
    final RTestUnitTestProxy test3 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test3"));
    assertEquals("suite2", test3.getParent().getName());
  }
}
