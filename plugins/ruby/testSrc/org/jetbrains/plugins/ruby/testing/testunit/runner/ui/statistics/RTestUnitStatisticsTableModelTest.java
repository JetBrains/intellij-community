package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.util.ui.SortableColumnModel;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitResultsForm;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsTableModelTest extends BaseRUnitTestsTestCase {
  private RTestUnitStatisticsTableModel myStatisticsTableModel;
  private RTestUnitResultsForm.FormSelectionListener mySelectionListener;
  private RTestUnitTestProxy myRootSuite;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myStatisticsTableModel = new RTestUnitStatisticsTableModel();
    mySelectionListener = myStatisticsTableModel.createSelectionListener();

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
