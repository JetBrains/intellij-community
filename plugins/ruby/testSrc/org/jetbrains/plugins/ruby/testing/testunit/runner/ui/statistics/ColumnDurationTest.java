package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.support.UITestUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class ColumnDurationTest extends BaseColumnRenderingTest {
  
  public void testValueOf_NotRun() {
    assertEquals("<NOT RUN>", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_InProgress() {
    mySimpleTest.setStarted();
    assertEquals("<RUNNING>", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestFailure() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestPassed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    assertEquals("<UNKNOWN>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_TestTerminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("<TERMINATED>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals("TERMINATED: " + String.valueOf((float)10) + " s", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_SuiteEmpty() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    suite.setStarted();
    suite.setFinished();
    assertEquals("<NO TESTS>", myColumn.valueOf(suite));

    suite.setFinished();
    assertEquals("<NO TESTS>", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteNotRun() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    assertEquals("<NOT RUN>", myColumn.valueOf(suite));

    final RTestUnitTestProxy test = createTestProxy("test", suite);
    assertEquals("<NOT RUN>", myColumn.valueOf(suite));

    test.setDuration(5);
    assertEquals("<NOT RUN>", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteFailed() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setTestFailed("", "", false);
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteError() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setTestFailed("", "", true);
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumn.valueOf(suite));
  }

  public void testValueOf_SuitePassed() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setFinished();
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteTerminated() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    suite.setTerminated();

    assertEquals("<TERMINATED>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals("TERMINATED: " + String.valueOf((float)10) + " s", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteRunning() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setStarted();

    assertEquals("<RUNNING>", myColumn.valueOf(suite));

    test.setDuration(10000);
    assertEquals("RUNNING: " + String.valueOf((float)10) + " s", myColumn.valueOf(suite));
  }

  public void testTotal_Test() {
    mySuite.addChild(mySimpleTest);

    doRender(mySimpleTest, 0);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(myColumn.valueOf(mySimpleTest), myFragmentsContainer.getTextAt(0));

    myFragmentsContainer.clear();
    doRender(mySimpleTest, 1);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(myColumn.valueOf(mySimpleTest), myFragmentsContainer.getTextAt(0));
  }

  public void testTotal_RegularSuite() {
    doRender(mySuite, 1);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(myColumn.valueOf(mySuite), myFragmentsContainer.getTextAt(0));
  }

  public void testTotal_TotalSuite() {
    doRender(mySuite, 0);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(myColumn.valueOf(mySuite), myFragmentsContainer.getTextAt(0));
  }
  
  protected ColoredRenderer createRenderer(final RTestUnitTestProxy rTestUnitTestProxy,
                                                    final UITestUtil.FragmentsContainer fragmentsContainer) {
    return new MyRenderer(rTestUnitTestProxy, fragmentsContainer);
  }

  protected ColumnInfo<RTestUnitTestProxy, String> createColumn() {
    return new ColumnDuration();
  }

  private class MyRenderer extends ColumnDuration.DurationCellRenderer {
    private UITestUtil.FragmentsContainer myFragmentsContainer;

    public MyRenderer(final RTestUnitTestProxy proxy,
                       final UITestUtil.FragmentsContainer fragmentsContainer) {
      super(proxy);
      myFragmentsContainer = fragmentsContainer;
    }

    @Override
    public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes,
                       final boolean isMainText) {
      myFragmentsContainer.append(fragment, attributes);
    }
  }
}
