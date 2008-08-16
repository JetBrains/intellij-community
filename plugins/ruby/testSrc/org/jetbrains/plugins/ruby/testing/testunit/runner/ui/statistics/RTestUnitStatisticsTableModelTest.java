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

  public void testGotoParentSuite_ResultsRoot() {
    // create test sturcure
    final RTestUnitTestProxy rootSuite = createSuiteProxy("rootSuite");

    final RTestUnitTestProxy suite3 = createSuiteProxy("A_suite3", rootSuite);
    final RTestUnitTestProxy failedTest31 = createTestProxy("failedTest31", suite3);
    final RTestUnitTestProxy errorTest31 = createTestProxy("errorTest31", suite3);
    doFailTest(failedTest31);
    doErrorTest(errorTest31);

    final RTestUnitTestProxy suite1 = createSuiteProxy("B_suite1", rootSuite);
    final RTestUnitTestProxy passedTest11 = createTestProxy("passedTest11", suite1);
    final RTestUnitTestProxy passedTest12 = createTestProxy("passedTest12", suite1);
    doPassTest(passedTest11);
    doPassTest(passedTest12);

    final RTestUnitTestProxy suite2 = createSuiteProxy("C_suite1", rootSuite);
    final RTestUnitTestProxy passedTest21 = createTestProxy("passedTest21", suite2);
    final RTestUnitTestProxy errorTest21 = createTestProxy("errorTest21", suite2);
    doPassTest(passedTest21);
    doErrorTest(errorTest21);

    final RTestUnitTestProxy suite4 = createSuiteProxy("D_suite4", rootSuite);
    final RTestUnitTestProxy failedTest41 = createTestProxy("failedTest41", suite4);
    final RTestUnitTestProxy errorTest41 = createTestProxy("errorTest41", suite4);
    final RTestUnitTestProxy errorTest42 = createTestProxy("errorTest42", suite4);
    doFailTest(failedTest41);
    doErrorTest(errorTest41);
    doErrorTest(errorTest42);

    final RTestUnitTestProxy passedTest1 = createTestProxy("passedTest1", rootSuite);
    final RTestUnitTestProxy failedTest1 = createTestProxy("failedTest1", rootSuite);
    final RTestUnitTestProxy errorTest1 = createTestProxy("errotTest1", rootSuite);
    doPassTest(passedTest1);
    doFailTest(failedTest1);
    doErrorTest(errorTest1);

    mySelectionListener.onSelectedRequest(rootSuite);

    //sort with another sort type
    myStatisticsTableModel.sortByColumn(0, SortableColumnModel.SORT_ASCENDING);
    //resort
    myStatisticsTableModel.sortByColumn(2, SortableColumnModel.SORT_DESCENDING);
    assertOrderedEquals(getItems(),
                        rootSuite, suite4, suite3, suite2, suite1, errorTest1, failedTest1, passedTest1);
    //reverse
    myStatisticsTableModel.sortByColumn(2, SortableColumnModel.SORT_ASCENDING);
    assertOrderedEquals(getItems(),
                        rootSuite, passedTest1, failedTest1, errorTest1, suite1, suite2, suite3, suite4);
    //direct
    myStatisticsTableModel.sortByColumn(2, SortableColumnModel.SORT_DESCENDING);
    assertOrderedEquals(getItems(),
                        rootSuite, suite4, suite3, suite2, suite1, errorTest1, failedTest1, passedTest1);
  }

  private List<RTestUnitTestProxy> getItems() {
    return myStatisticsTableModel.getItems();
  }
}
