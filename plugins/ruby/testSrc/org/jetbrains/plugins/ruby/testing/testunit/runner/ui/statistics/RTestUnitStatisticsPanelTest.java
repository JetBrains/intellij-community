package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.Marker;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitTestProxySelectionListener;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestProxyTreeSelectionListener;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitStatisticsPanelTest extends BaseRUnitTestsTestCase {
  private RTestUnitStatisticsPanel myRTestUnitStatisticsPanel;
  private TestProxyTreeSelectionListener mySelectionListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRTestUnitStatisticsPanel = new RTestUnitStatisticsPanel();
    mySelectionListener = myRTestUnitStatisticsPanel.createSelectionListener();
  }

  public void testGotoSuite_OnTest() {
    // create test sturcure
    final RTestUnitTestProxy rootSuite = createSuiteProxy("rootSuite");
    final RTestUnitTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final RTestUnitTestProxy test1 = createTestProxy("test1", suite1);

    // show suite in table
    mySelectionListener.onSelected(suite1);
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
    mySelectionListener.onSelected(rootSuite);
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
    mySelectionListener.onSelected(suite1);
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
    mySelectionListener.onSelected(rootSuite);
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

  public void testChangeSelectionAction() {
    final Marker onSelectedHappend = new Marker();
    final Ref<RTestUnitTestProxy> proxyRef = new Ref<RTestUnitTestProxy>();
    final Ref<Boolean> focusRequestedRef = new Ref<Boolean>();

    myRTestUnitStatisticsPanel.addSelectionListener(new RTestUnitTestProxySelectionListener() {
      public void onSelected(@Nullable final RTestUnitTestProxy selectedTestProxy, final boolean requestFocus) {
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
    mySelectionListener.onSelected(suite1);
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

    mySelectionListener.onSelected(rootSuite);
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

    mySelectionListener.onSelected(rootSuite);
    myRTestUnitStatisticsPanel.selectRow(0);
    assertEquals(rootSuite, myRTestUnitStatisticsPanel.getSelectedItem());

    myRTestUnitStatisticsPanel.createChangeSelectionAction().run();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(rootSuite, proxyRef.get());
    assertTrue(focusRequestedRef.get());
  }
}
