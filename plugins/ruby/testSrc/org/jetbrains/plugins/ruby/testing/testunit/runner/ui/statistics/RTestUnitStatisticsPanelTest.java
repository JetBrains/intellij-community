package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.Marker;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitResultsForm;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitTestProxySelectionChangedListener;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsPanelTest extends BaseRUnitTestsTestCase {
  private RTestUnitStatisticsPanel myRTestUnitStatisticsPanel;
  private RTestUnitResultsForm.FormSelectionListener mySelectionListener;
  private RTestUnitEventsListener myTestEventsListener;
  private RTestUnitTestProxy myRootSuite;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRootSuite = createSuiteProxy("root");

    myRTestUnitStatisticsPanel = new RTestUnitStatisticsPanel();
    mySelectionListener = myRTestUnitStatisticsPanel.createSelectionListener();
    myTestEventsListener = myRTestUnitStatisticsPanel.createTestEventsListener();
  }

  public void testGotoSuite_OnTest() {
    // create test sturcure
    final RTestUnitTestProxy rootSuite = createSuiteProxy("rootSuite");
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);

    // show suite in table
    mySelectionListener.onSelectedRequest(suite1);
    // selects row that corresponds to test1
    myRTestUnitStatisticsPanel.selectRow(1);

    // Check that necessary row is selected
    assertEquals(test1, myRTestUnitStatisticsPanel.getSelectedItem());

    // Perform action on test
    myRTestUnitStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table wasn't changed.
    // For it let's select Total row and check selected object
    myRTestUnitStatisticsPanel.selectRow(0);
    assertEquals(suite1, myRTestUnitStatisticsPanel.getSelectedItem());
  }

  public void testGotoSuite_OnSuite() {
    // create test sturcure
    final RTestUnitTestProxy rootSuite = createSuiteProxy("rootSuite");
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", rootSuite);

    // show root suite in table
    mySelectionListener.onSelectedRequest(rootSuite);
    // selects row that corresponds to suite1
    myRTestUnitStatisticsPanel.selectRow(1);

    // Check that necessary row is selected
    assertEquals(suite1, myRTestUnitStatisticsPanel.getSelectedItem());

    // Perform action on suite
    myRTestUnitStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table was changed.
    // For it let's select Total row and check selected object
    myRTestUnitStatisticsPanel.selectRow(0);
    assertEquals(suite1, myRTestUnitStatisticsPanel.getSelectedItem());
  }

  public void testGotoParentSuite_Total() {
    // create test sturcure
    final RTestUnitTestProxy rootSuite = createSuiteProxy("rootSuite");
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", rootSuite);

    // show suite in table
    mySelectionListener.onSelectedRequest(suite1);
    // selects Total row
    myRTestUnitStatisticsPanel.selectRow(0);

    // Check that necessary row is selected
    assertEquals(suite1, myRTestUnitStatisticsPanel.getSelectedItem());

    // Perform action on suite
    myRTestUnitStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table was changed.
    // For it let's select Total row and check selected object
    myRTestUnitStatisticsPanel.selectRow(0);
    assertEquals(rootSuite, myRTestUnitStatisticsPanel.getSelectedItem());
  }

  public void testGotoParentSuite_TotalRoot() {
    // create test sturcure
    final RTestUnitTestProxy rootSuite = createSuiteProxy("rootSuite");
    createSuiteProxy("suite1", rootSuite);

    // show root suite in table
    mySelectionListener.onSelectedRequest(rootSuite);
    // selects Total row
    myRTestUnitStatisticsPanel.selectRow(0);

    // Check that necessary row is selected
    assertEquals(rootSuite, myRTestUnitStatisticsPanel.getSelectedItem());

    // Perform action on suite
    myRTestUnitStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table wasn't changed.
    // For it let's select Total row and check selected object
    myRTestUnitStatisticsPanel.selectRow(0);
    assertEquals(rootSuite, myRTestUnitStatisticsPanel.getSelectedItem());
  }

  public void testChangeSelectionListener() {
    // create data fixture
    final RTestUnitTestProxy rootSuite = createSuiteProxy("rootSuite");
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);

    //test
    mySelectionListener.onSelectedRequest(test1);
    assertEquals(test1, myRTestUnitStatisticsPanel.getSelectedItem());

    //suite
    mySelectionListener.onSelectedRequest(suite1);
    assertEquals(suite1, myRTestUnitStatisticsPanel.getSelectedItem());
  }

  public void testChangeSelectionAction() {
    final Marker onSelectedHappend = new Marker();
    final Ref<RTestUnitTestProxy> proxyRef = new Ref<RTestUnitTestProxy>();
    final Ref<Boolean> focusRequestedRef = new Ref<Boolean>();

    myRTestUnitStatisticsPanel.addChangeSelectionListener(new RTestUnitTestProxySelectionChangedListener() {
      public void onChangeSelection(@Nullable final RTestUnitTestProxy selectedTestProxy, final boolean requestFocus) {
        onSelectedHappend.set();
        proxyRef.set(selectedTestProxy);
        focusRequestedRef.set(requestFocus);
      }
    });

    // create data fixture
    final RTestUnitTestProxy rootSuite = createSuiteProxy("rootSuite");
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);

    //on test
    mySelectionListener.onSelectedRequest(suite1);
    myRTestUnitStatisticsPanel.selectRow(1);
    assertEquals(test1, myRTestUnitStatisticsPanel.getSelectedItem());

    myRTestUnitStatisticsPanel.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(test1, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on suite
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    mySelectionListener.onSelectedRequest(rootSuite);
    myRTestUnitStatisticsPanel.selectRow(1);
    assertEquals(suite1, myRTestUnitStatisticsPanel.getSelectedItem());

    myRTestUnitStatisticsPanel.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(suite1, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on Total
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    mySelectionListener.onSelectedRequest(rootSuite);
    myRTestUnitStatisticsPanel.selectRow(0);
    assertEquals(rootSuite, myRTestUnitStatisticsPanel.getSelectedItem());

    myRTestUnitStatisticsPanel.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(rootSuite, proxyRef.get());
    assertTrue(focusRequestedRef.get());
  }

  public void testOnSuiteStarted_NoCurrent() {
    mySelectionListener.onSelectedRequest(null);

    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onSuiteStarted(suite1);
    assertEmpty(getItems());
  }

  public void testOnSuiteStarted_Current() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(suite);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteStarted_Child() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(test1);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteStarted_Other() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite", myRootSuite);
    final RTestUnitTestProxy other_suite = createSuiteProxy("other_suite", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    createTestProxy("test1", suite);
    createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(other_suite);
    assertSameElements(getItems(), suite);
  }

  public void testOnSuiteFinished_NoCurrent() {
    mySelectionListener.onSelectedRequest(null);

    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onSuiteFinished(suite1);
    assertEmpty(getItems());
  }

  public void testOnSuiteFinished_Current() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(suite);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteFinished_Child() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(test1);
    assertSameElements(getItems(), suite, test1, test2);
  }

  public void testOnSuiteFinished_Other() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite", myRootSuite);
    final RTestUnitTestProxy other_suite = createSuiteProxy("other_suite", myRootSuite);

    mySelectionListener.onSelectedRequest(suite);
    assertSameElements(getItems(), suite);

    createTestProxy("test1", suite);
    createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(other_suite);
    assertSameElements(getItems(), suite);
  }

  public void testOnTestStarted_NoCurrent() {
    mySelectionListener.onSelectedRequest(null);

    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onTestStarted(test1);
    assertEmpty(getItems());
  }

  public void testOnTestStarted_Child() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);

    mySelectionListener.onSelectedRequest(test1);
    assertSameElements(getItems(), myRootSuite, test1);

    final RTestUnitTestProxy test2 = createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestStarted(test1);
    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnTestStarted_Other() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);

    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy other_test = createTestProxy("other_test", suite);

    mySelectionListener.onSelectedRequest(test1);
    assertSameElements(getItems(), myRootSuite, test1, suite);

    createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestStarted(other_test);
    assertSameElements(getItems(), myRootSuite, test1, suite);
  }

  public void testOnTestFinished_NoCurrent() {
    mySelectionListener.onSelectedRequest(null);

    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onTestFinished(test1);
    assertEmpty(getItems());

  }

  public void testOnTestFinished_Child() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);

    mySelectionListener.onSelectedRequest(test1);
    assertSameElements(getItems(), myRootSuite, test1);

    final RTestUnitTestProxy test2 = createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestFinished(test1);
    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnTestFinished_Other() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);

    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy other_test = createTestProxy("other_test", suite);

    mySelectionListener.onSelectedRequest(test1);
    assertSameElements(getItems(), myRootSuite, test1, suite);

    createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestFinished(other_test);
    assertSameElements(getItems(), myRootSuite, test1, suite);
  }

  public void testSelectionRestoring_ForTest() {
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite);

    mySelectionListener.onSelectedRequest(test1);

    final RTestUnitTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onTestStarted(test2);

    assertEquals(test1, myRTestUnitStatisticsPanel.getSelectedItem());
  }

  public void testSelectionRestoring_ForSuite() {
    mySelectionListener.onSelectedRequest(myRootSuite);

    // another suite was added. Model should be updated
    final RTestUnitTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    myTestEventsListener.onSuiteStarted(suite);

    assertEquals(myRootSuite, myRTestUnitStatisticsPanel.getSelectedItem());
  }

  private List<RTestUnitTestProxy> getItems() {
    return myRTestUnitStatisticsPanel.getTableItems();
  }
}
