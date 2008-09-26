package org.jetbrains.plugins.ruby.testing.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.Marker;
import org.jetbrains.plugins.ruby.testing.sm.runner.BaseSMTRunnerTestCase;
import org.jetbrains.plugins.ruby.testing.sm.runner.GeneralToSMTRunnerEventsConvertor;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * @author Roman Chernyatchik
 */
public class SMTestRunnerResultsFormTest extends BaseSMTRunnerTestCase {
  private SMTRunnerConsoleView myConsole;
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private TreeModel myTreeModel;
  private SMTestRunnerResultsForm myResultsViewer;
  private TestConsoleProperties myConsoleProperties;
  private SMTestProxy myTestsRootNode;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myConsoleProperties = createConsoleProperties();
    TestConsoleProperties.HIDE_PASSED_TESTS.set(myConsoleProperties, false);
    TestConsoleProperties.OPEN_FAILURE_LINE.set(myConsoleProperties, false);
    TestConsoleProperties.SCROLL_TO_SOURCE.set(myConsoleProperties, false);
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myConsoleProperties, false);
    TestConsoleProperties.TRACK_RUNNING_TEST.set(myConsoleProperties, false);

    myResultsViewer = (SMTestRunnerResultsForm)createResultsViewer(myConsoleProperties);
    myTestsRootNode = myResultsViewer.getTestsRootNode();

    myConsole = new SMTRunnerConsoleView(myConsoleProperties, myResultsViewer);
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(myResultsViewer.getTestsRootNode());
    myEventsProcessor.addEventsListener(myResultsViewer);
    myTreeModel = myResultsViewer.getTreeView().getModel();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myEventsProcessor);
    Disposer.dispose(myConsole);

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

    myResultsViewer.setShowStatisticForProxyHandler(new TestProxySelectionChangedListener() {
      public void onChangeSelection(@Nullable final SMTestProxy selectedTestProxy, @NotNull final Object sender,
                                    final boolean requestFocus) {
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
    myResultsViewer.showStatisticsForSelectedProxy();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(test, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on suite
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    myResultsViewer.selectAndNotify(suite);
    myResultsViewer.showStatisticsForSelectedProxy();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(suite, proxyRef.get());
    assertTrue(focusRequestedRef.get());
  }

  public void testRuby_1767() throws InterruptedException {
    TestConsoleProperties.HIDE_PASSED_TESTS.set(myConsoleProperties, true);

    myEventsProcessor.onStartTesting();
    myEventsProcessor.onSuiteStarted("suite", null);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted("test_failed", null);
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFailure("test_failed", "", "", false);
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFinished("test_failed", 10);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted("test", null);
    myResultsViewer.performUpdate();
    assertEquals(2, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));

    myEventsProcessor.onTestFinished("test", 10);
    assertEquals(2, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));

    myEventsProcessor.onSuiteFinished("suite");
    myEventsProcessor.onFinishTesting();

    assertEquals(1, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));
  }

  public void testExpandIfOnlyOneRootChild() throws InterruptedException {
    myEventsProcessor.onStartTesting();
    myEventsProcessor.onSuiteStarted("suite1", null);
    myResultsViewer.performUpdate();
    myEventsProcessor.onSuiteStarted("suite2", null);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted("test_failed", null);
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFailure("test_failed", "", "", false);
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFinished("test_failed", 10);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted("test", null);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestFinished("test", 10);
    myResultsViewer.performUpdate();

    myEventsProcessor.onSuiteFinished("suite2");
    myResultsViewer.performUpdate();
    myEventsProcessor.onSuiteFinished("suite1");
    myResultsViewer.performUpdate();
    myEventsProcessor.onFinishTesting();
    myResultsViewer.performUpdate();

    final DefaultMutableTreeNode suite1Node =
        (DefaultMutableTreeNode)myTreeModel.getChild(myTreeModel.getRoot(), 0);
    final DefaultMutableTreeNode suite2Node =
        (DefaultMutableTreeNode)myTreeModel.getChild(suite1Node, 0);

    assertTrue(myResultsViewer.getTreeView().isExpanded(new TreePath(suite1Node.getPath())));
    assertFalse(myResultsViewer.getTreeView().isExpanded(new TreePath(suite2Node.getPath())));
  }
}
