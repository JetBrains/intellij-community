package org.jetbrains.plugins.ruby.testing.sm.runner.ui.statistics;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.Marker;
import org.jetbrains.plugins.ruby.testing.sm.runner.BaseSMTRunnerTestCase;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTRunnerEventsListener;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.SMTestRunnerResultsForm;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.TestProxySelectionChangedListener;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsPanelTest extends BaseSMTRunnerTestCase {
  private StatisticsPanel myStatisticsPanel;
  private SMTestRunnerResultsForm.FormSelectionListener mySelectionListener;
  private SMTRunnerEventsListener myTestEventsListener;
  private SMTestProxy myRootSuite;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRootSuite = createSuiteProxy("root");

    myStatisticsPanel = new StatisticsPanel(getProject());
    mySelectionListener = myStatisticsPanel.createSelectionListener();
    myTestEventsListener = myStatisticsPanel.createTestEventsListener();
  }

  public void testGotoSuite_OnTest() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);

    // show suite in table
    mySelectionListener.onSelectedRequest(suite1);
    // selects row that corresponds to test1
    myStatisticsPanel.selectRow(1);

    // Check that necessary row is selected
    assertEquals(test1, myStatisticsPanel.getSelectedItem());

    // Perform action on test
    myStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table wasn't changed.
    // For it let's select Total row and check selected object
    myStatisticsPanel.selectRow(0);
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());
  }

  public void testGotoSuite_OnSuite() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);

    // show root suite in table
    mySelectionListener.onSelectedRequest(rootSuite);
    // selects row that corresponds to suite1
    myStatisticsPanel.selectRow(1);

    // Check that necessary row is selected
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());

    // Perform action on suite
    myStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table was changed.
    // For it let's select Total row and check selected object
    myStatisticsPanel.selectRow(0);
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());
  }

  public void testGotoParentSuite_Total() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);

    // show suite in table
    mySelectionListener.onSelectedRequest(suite1);
    // selects Total row
    myStatisticsPanel.selectRow(0);

    // Check that necessary row is selected
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());

    // Perform action on suite
    myStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table was changed.
    // For it let's select Total row and check selected object
    myStatisticsPanel.selectRow(0);
    assertEquals(rootSuite, myStatisticsPanel.getSelectedItem());
  }

  public void testGotoParentSuite_TotalRoot() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    createSuiteProxy("suite1", rootSuite);

    // show root suite in table
    mySelectionListener.onSelectedRequest(rootSuite);
    // selects Total row
    myStatisticsPanel.selectRow(0);

    // Check that necessary row is selected
    assertEquals(rootSuite, myStatisticsPanel.getSelectedItem());

    // Perform action on suite
    myStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table wasn't changed.
    // For it let's select Total row and check selected object
    myStatisticsPanel.selectRow(0);
    assertEquals(rootSuite, myStatisticsPanel.getSelectedItem());
  }

  public void testChangeSelectionListener() {
    // create data fixture
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);

    //test
    mySelectionListener.onSelectedRequest(test1);
    assertEquals(test1, myStatisticsPanel.getSelectedItem());

    //suite
    mySelectionListener.onSelectedRequest(suite1);
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());
  }

  public void testChangeSelectionAction() {
    final Marker onSelectedHappend = new Marker();
    final Ref<SMTestProxy> proxyRef = new Ref<SMTestProxy>();
    final Ref<Boolean> focusRequestedRef = new Ref<Boolean>();

    myStatisticsPanel.addChangeSelectionListener(new TestProxySelectionChangedListener() {
      public void onChangeSelection(@Nullable final SMTestProxy selectedTestProxy, @NotNull final Object sender,
                                    final boolean requestFocus) {
        onSelectedHappend.set();
        proxyRef.set(selectedTestProxy);
        focusRequestedRef.set(requestFocus);
      }
    });

    // create data fixture
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);

    //on test
    mySelectionListener.onSelectedRequest(suite1);
    myStatisticsPanel.selectRow(1);
    assertEquals(test1, myStatisticsPanel.getSelectedItem());

    myStatisticsPanel.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(test1, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on suite
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    mySelectionListener.onSelectedRequest(rootSuite);
    myStatisticsPanel.selectRow(1);
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());

    myStatisticsPanel.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(suite1, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on Total
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    mySelectionListener.onSelectedRequest(rootSuite);
    myStatisticsPanel.selectRow(0);
    assertEquals(rootSuite, myStatisticsPanel.getSelectedItem());

    myStatisticsPanel.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(rootSuite, proxyRef.get());
    assertTrue(focusRequestedRef.get());
  }

  public void testOnSuiteStarted_NoCurrent() {
    mySelectionListener.onSelectedRequest(null);

    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onSuiteStarted(suite1);
    assertEmpty(getItems());
  }

  public void testOnSuiteStarted_Current() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(suite);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteStarted_Child() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(test1);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteStarted_Other() {
    final SMTestProxy suite = createSuiteProxy("suite", myRootSuite);
    final SMTestProxy other_suite = createSuiteProxy("other_suite", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    createTestProxy("test1", suite);
    createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(other_suite);
    assertSameElements(getItems(), suite);
  }

  public void testOnSuiteFinished_NoCurrent() {
    mySelectionListener.onSelectedRequest(null);

    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onSuiteFinished(suite1);
    assertEmpty(getItems());
  }

  public void testOnSuiteFinished_Current() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(suite);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteFinished_Child() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(test1);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteFinished_Other() {
    final SMTestProxy suite = createSuiteProxy("suite", myRootSuite);
    final SMTestProxy other_suite = createSuiteProxy("other_suite", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    createTestProxy("test1", suite);
    createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(other_suite);
    assertSameElements(getItems(), suite);
  }

  public void testOnTestStarted_NoCurrent() {
    mySelectionListener.onSelectedRequest(null);

    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onTestStarted(test1);
    assertEmpty(getItems());
  }

  public void testOnTestStarted_Child() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);

    mySelectionListener.onSelectedRequest(test1);
    assertSameElements(getItems(), myRootSuite, test1);

    final SMTestProxy test2 = createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestStarted(test1);
    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnTestStarted_Other() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);

    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy other_test = createTestProxy("other_test", suite);

    mySelectionListener.onSelectedRequest(test1);
    assertSameElements(getItems(), myRootSuite, test1, suite);

    createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestStarted(other_test);
    assertSameElements(getItems(), myRootSuite, test1, suite);
  }

  public void testOnTestFinished_NoCurrent() {
    mySelectionListener.onSelectedRequest(null);

    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onTestFinished(test1);
    assertEmpty(getItems());

  }

  public void testOnTestFinished_Child() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);

    mySelectionListener.onSelectedRequest(test1);
    assertSameElements(getItems(), myRootSuite, test1);

    final SMTestProxy test2 = createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestFinished(test1);
    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnTestFinished_Other() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);

    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy other_test = createTestProxy("other_test", suite);

    mySelectionListener.onSelectedRequest(test1);
    assertSameElements(getItems(), myRootSuite, test1, suite);

    createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestFinished(other_test);
    assertSameElements(getItems(), myRootSuite, test1, suite);
  }

  public void testSelectionRestoring_ForTest() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite);

    mySelectionListener.onSelectedRequest(test1);

    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onTestStarted(test2);

    assertEquals(test1, myStatisticsPanel.getSelectedItem());
  }

  public void testSelectionRestoring_ForSuite() {
    mySelectionListener.onSelectedRequest(myRootSuite);

    // another suite was added. Model should be updated
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    myTestEventsListener.onSuiteStarted(suite);

    assertEquals(myRootSuite, myStatisticsPanel.getSelectedItem());
  }

  private List<SMTestProxy> getItems() {
    return myStatisticsPanel.getTableItems();
  }
}
