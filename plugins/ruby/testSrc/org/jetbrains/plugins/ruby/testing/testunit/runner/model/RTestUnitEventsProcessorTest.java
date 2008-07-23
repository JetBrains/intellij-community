package org.jetbrains.plugins.ruby.testing.testunit.runner.model;

import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.properties.RTestUnitConsoleProperties;
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
  private RTestUnitEventsProcessor myEventsProcessor;
  private TreeModel myTreeModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final RTestsRunConfiguration runConfiguration = createRTestsRunConfiguration();

    final RTestUnitConsoleProperties consoleProperties = new RTestUnitConsoleProperties(runConfiguration);
    myConsole = new RTestUnitConsoleView(runConfiguration, consoleProperties);

    final RTestUnitResultsForm resultsForm = myConsole.getResultsForm();
    myEventsProcessor = new RTestUnitEventsProcessor(resultsForm);
    myTreeModel = resultsForm.getTreeView().getModel();

    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    myEventsProcessor.onFinishTesting();
    myConsole.dispose();

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
    assertTrue(!nodeDescriptor.expandOnDoubleClick());

    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertTrue(rootProxy.wasRun());
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

    assertTrue(proxy != null);
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
    assertTrue(!proxy.isInProgress());
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


    assertTrue(!proxy.isDefect());
    assertTrue(!proxy.isInProgress());

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

  public void testTestingFinished() {
    myEventsProcessor.onFinishTesting();
    assertTrue(myEventsProcessor.getEndTime() > 0);

    //Tree
     final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(0, myTreeModel.getChildCount(rootTreeNode));
    final RTestUnitTestProxy rootProxy = RTestUnitTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertTrue(!rootProxy.isInProgress());
    assertTrue(!rootProxy.isDefect());
  }

  public void testTestingFinished_withDefect() {
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

    assertTrue(!rootProxy.isInProgress());
    assertTrue(rootProxy.isDefect());
  }

  public void testTestingFinished_twice() {
    myEventsProcessor.onFinishTesting();

    final long time = myEventsProcessor.getEndTime();
    myEventsProcessor.onFinishTesting();
    assertEquals(time, myEventsProcessor.getEndTime());
  }
}
