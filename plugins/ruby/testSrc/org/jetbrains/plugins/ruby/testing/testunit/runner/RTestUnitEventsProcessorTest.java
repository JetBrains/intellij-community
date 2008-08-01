package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.plugins.ruby.Marker;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitConsoleView;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitResultsForm;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitTestTreeView;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitEventsProcessorTest extends BaseRUnitTestsTestCase {
  private RTestUnitConsoleView myConsole;
  private GeneralToRTestUnitEventsConvertor myEventsProcessor;
  private TreeModel myTreeModel;
  private RTestUnitResultsForm myResultsViewer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final RTestUnitConsoleProperties consoleProperties = createConsoleProperties();
    myResultsViewer = (RTestUnitResultsForm)createResultsViewer(consoleProperties);

    myConsole = new RTestUnitConsoleView(consoleProperties, myResultsViewer);
    myEventsProcessor = new GeneralToRTestUnitEventsConvertor(myResultsViewer.getTestsRootNode());
    myEventsProcessor.addEventsListener(myResultsViewer);
    myTreeModel = myResultsViewer.getTreeView().getModel();

    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    myEventsProcessor.onFinishTesting();
    Disposer.dispose(myConsole);

    super.tearDown();
  }

  public void testOnStartTesting() {
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

  public void testOnTestStart() throws InterruptedException {
    onTestStart("some_test");
    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final RTestUnitTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

    assertNotNull(proxy);
    assertTrue(proxy.isInProgress());

    final Object rootTreeNode = (myTreeModel.getRoot());
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);
    assertSameElements(rootProxy.getChildren(), proxy);


    onTestStart("some_test2");
    final String fullName2 = myEventsProcessor.getFullTestName("some_test2");
    final RTestUnitTestProxy proxy2 = myEventsProcessor.getProxyByFullTestName(fullName2);
    assertSameElements(rootProxy.getChildren(), proxy, proxy2);
  }

  public void testOnTestStart_Twice() {
    onTestStart("some_test");
    onTestStart("some_test");

    assertEquals(1, myEventsProcessor.getRunningTestsQuantity());
  }

  public void testOnTestFailure() {
    onTestStart("some_test");
    myEventsProcessor.onTestFailure("some_test", "", "");

    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final RTestUnitTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

    assertTrue(proxy.isDefect());
    assertFalse(proxy.isInProgress());
  }

  public void testOnTestFailure_Twice() {
    onTestStart("some_test");
    myEventsProcessor.onTestFailure("some_test", "", "");
    myEventsProcessor.onTestFailure("some_test", "", "");

    assertEquals(1, myEventsProcessor.getRunningTestsQuantity());
    assertEquals(1, myEventsProcessor.getFailedTestsSet().size());

  }

  public void testOnTestFinish() {
    onTestStart("some_test");
    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final RTestUnitTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);
    myEventsProcessor.onTestFinish("some_test");

    assertEquals(0, myEventsProcessor.getRunningTestsQuantity());
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

  public void testOnTestFinish_EmptySuite() {
    myEventsProcessor.onFinishTesting();

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(0, myTreeModel.getChildCount(rootTreeNode));
    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertFalse(rootProxy.isInProgress());
    assertTrue(rootProxy.isDefect());
  }

  public void testOnFinishTesting_WithDefect() {
    onTestStart("test");
    myEventsProcessor.onTestFailure("test", "", "");
    myEventsProcessor.onTestFinish("test");
    myEventsProcessor.onFinishTesting();

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertFalse(rootProxy.isInProgress());
    assertTrue(rootProxy.isDefect());
  }

  public void testOnFinishTesting_twice() {
    myEventsProcessor.onFinishTesting();

    final Marker finishedMarker = new Marker();
    myEventsProcessor.addEventsListener(new RTestUnitEventsAdapter(){
      @Override
      public void onTestingFinished() {
        finishedMarker.set();
      }
    });
    myEventsProcessor.onFinishTesting();
    assertFalse(finishedMarker.isSet());
  }

  public void testOnSuiteStart() {
    onTestSuiteStart("suite1");

    //lets check that new tests have right parent
    onTestStart("test1");
    final RTestUnitTestProxy test1 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test1"));
    assertEquals("suite1", test1.getParent().getName());

    //lets check that new suits have righ parent
    onTestSuiteStart("suite2");
    onTestSuiteStart("suite3");
    onTestStart("test2");
    final RTestUnitTestProxy test2 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test2"));
    assertEquals("suite3", test2.getParent().getName());
    assertEquals("suite2", test2.getParent().getParent().getName());

    myEventsProcessor.onTestFinish("test2");

    //check that after finishing suite (suite3), current will be parent of finished suite (i.e. suite2)
    myEventsProcessor.onTestSuiteFinish("suite3");
    onTestStart("test3");
    final RTestUnitTestProxy test3 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test3"));
    assertEquals("suite2", test3.getParent().getName());

    //clean up
    myEventsProcessor.onTestSuiteFinish("suite2");
    myEventsProcessor.onTestSuiteFinish("suite1");
  }

  private void onTestStart(final String testName) {
    myEventsProcessor.onTestStart(testName);
    myResultsViewer.performUpdate();
  }

  private void onTestSuiteStart(final String suiteName) {
    myEventsProcessor.onTestSuiteStart(suiteName);
    myResultsViewer.performUpdate();
  }
}
