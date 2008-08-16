package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.util.ui.SortableColumnModel;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitEventsListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitResultsForm;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsTableModelTest extends BaseRUnitTestsTestCase {
  private RTestUnitStatisticsTableModel myStatisticsTableModel;
  private RTestUnitResultsForm.FormSelectionListener mySelectionListener;
  private RTestUnitEventsListener myTestEventsListener;
  private RTestUnitTestProxy myRootSuite;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myStatisticsTableModel = new RTestUnitStatisticsTableModel();
    mySelectionListener = myStatisticsTableModel.createSelectionListener(null);
    myTestEventsListener = myStatisticsTableModel.createTestEventsListener();

    myRootSuite = createSuiteProxy("root");
  }

  public void testOnSelected_Null() {
    mySelectionListener.onSelectedRequest(null);

    assertEmpty(getItems());
  }

  public void testOnSelected_Test() {
    final RTestUnitTestProxy test1 = createTestProxy("test1", myRootSuite);
    final RTestUnitTestProxy test2 = createTestProxy("test2", myRootSuite);
    mySelectionListener.onSelectedRequest(test1);

    assertSameElements(getItems(), myRootSuite, test1, test2);
  }

  public void testOnSelected_Suite() {
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);
    final RTestUnitTestProxy test2 = createTestProxy("test2", suite1);

    final RTestUnitTestProxy suite2 = createSuiteProxy("suite2", myRootSuite);

    mySelectionListener.onSelectedRequest(suite1);
    assertSameElements(getItems(), suite1, test1, test2);

    mySelectionListener.onSelectedRequest(suite2);
    assertSameElements(getItems(), suite2);

    mySelectionListener.onSelectedRequest(myRootSuite);
    assertSameElements(getItems(), myRootSuite, suite1, suite2);
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

  public void testSort_ColumnTest() {
    final RTestUnitTestProxy firstSuite = createSuiteProxy("K_suite1", myRootSuite);
    final RTestUnitTestProxy lastSuite = createSuiteProxy("L_suite1", myRootSuite);
    final RTestUnitTestProxy firstTest = createTestProxy("A_test", myRootSuite);
    final RTestUnitTestProxy lastTest = createTestProxy("Z_test", myRootSuite);

    mySelectionListener.onSelectedRequest(myRootSuite);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);

    //sort with another sort type
    myStatisticsTableModel.sortByColumn(2, SortableColumnModel.SORT_ASCENDING);
    //resort
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);
    //reverse
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_DESCENDING);
    assertOrderedEquals(getItems(), myRootSuite, lastTest, lastSuite, firstSuite, firstTest);
    //direct
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);
  }

  public void testSort_DurationTest() {
    final RTestUnitTestProxy firstSuite = createSuiteProxy("A_suite1", myRootSuite);
    final RTestUnitTestProxy firstSuite_Test = createTestProxy("test", firstSuite);
    firstSuite_Test.setDuration(10);

    final RTestUnitTestProxy lastSuite = createSuiteProxy("L_suite1", myRootSuite);
    final RTestUnitTestProxy lastSuite_Test = createTestProxy("test", lastSuite);
    lastSuite_Test.setDuration(90);

    final RTestUnitTestProxy firstTest = createTestProxy("K_test", myRootSuite);
    firstTest.setDuration(1);
    final RTestUnitTestProxy lastTest = createTestProxy("Z_test", myRootSuite);
    lastTest.setDuration(100);

    mySelectionListener.onSelectedRequest(myRootSuite);
    //assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);

    //sort with another sort type
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_ASCENDING);
    //resort
    myStatisticsTableModel.sortByColumn(1, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);
    //reverse
    myStatisticsTableModel.sortByColumn(1, SortableColumnModel.SORT_DESCENDING);
    assertOrderedEquals(getItems(), myRootSuite, lastTest, lastSuite, firstSuite, firstTest);
    //direct
    myStatisticsTableModel.sortByColumn(1, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(), myRootSuite, firstTest, firstSuite, lastSuite, lastTest);
  }

  private List<RTestUnitTestProxy> getItems() {
    return myStatisticsTableModel.getItems();
  }
}
