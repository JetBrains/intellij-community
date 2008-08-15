package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.support.UITestUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestsPresentationUtil;

/**
 * @author Roman Chernyatchik
 */
public class ColumnResultsTest extends BaseColumnRenderingTest {

  public void testPresentation_TestNotRun() {
    doRender(mySimpleTest);

    assertFragmentsSize(1);
    assertEquals(1, myFragmentsContainer.getFragments().size());
    assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Not run", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestInProgress() {
    mySimpleTest.setStarted();

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Running...", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestFailure() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Assertion failed", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestPassed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Passed", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Error", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_TestTerminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();

    doRender(mySimpleTest);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.TERMINATED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Terminated", myFragmentsContainer.getTextAt(0));
  }

  public void testValueOf_Test() {
    assertEquals("<underfined>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setStarted();
    assertEquals("<underfined>", myColumn.valueOf(mySimpleTest));

    mySimpleTest.setFinished();
    assertEquals("<underfined>", myColumn.valueOf(mySimpleTest));
  }

  public void testValueOf_Suite() {
    assertEquals("<underfined>", myColumn.valueOf(mySuite));

    mySuite.setStarted();
    assertEquals("<underfined>", myColumn.valueOf(mySuite));

    createTestProxy(mySuite);
    assertEquals("<underfined>", myColumn.valueOf(mySuite));

    mySuite.setFinished();
    assertEquals("<underfined>", myColumn.valueOf(mySuite));
  }

  public void testPresentation_SuiteNotRun() {
    doRender(mySuite);

    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("<NO TESTS>", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteEmpty() {
    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("<NO TESTS>", myFragmentsContainer.getTextAt(0));

    myFragmentsContainer.clear();
    mySuite.setStarted();
    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("<NO TESTS>", myFragmentsContainer.getTextAt(0));

    myFragmentsContainer.clear();
    mySuite.setFinished();
    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("<NO TESTS>", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteTestProgress() {
    mySuite.setStarted();
    final RTestUnitTestProxy test1 = createTestProxy(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    assertEmpty(myFragmentsContainer.getFragments());
  }

  public void testPresentation_SuiteTestPassed() {
    mySuite.setStarted();
    final RTestUnitTestProxy test1 = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    test1.setFinished();

    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("P:1", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteTestFailed() {
    mySuite.setStarted();
    final RTestUnitTestProxy test1 = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    test1.setTestFailed("", "", false);

    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("F:1 ", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteTestError() {
    mySuite.setStarted();
    final RTestUnitTestProxy test1 = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    test1.setTestFailed("", "", true);

    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("E:1 ", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteTerminated() {
    mySuite.setStarted();
    final RTestUnitTestProxy test1 = createTestProxy(mySuite);
    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    test1.setStarted();
    mySuite.setTerminated();

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());
  }

  public void testPresentation_SuiteTerminated_WithResults() {
    mySuite.setStarted();
    final RTestUnitTestProxy passedTest = createTestProxy(mySuite);
    final RTestUnitTestProxy failedTest = createTestProxy(mySuite);
    final RTestUnitTestProxy errorTest = createTestProxy(mySuite);
    final RTestUnitTestProxy inProgressTest = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    passedTest.setStarted();
    passedTest.setFinished();

    failedTest.setStarted();
    failedTest.setTestFailed("", "", false);
    failedTest.setFinished();

    errorTest.setStarted();
    errorTest.setTestFailed("", "", true);
    errorTest.setFinished();

    inProgressTest.setStarted();

    mySuite.setTerminated();

    doRender(mySuite);
    assertFragmentsSize(3);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("F:1 ", myFragmentsContainer.getTextAt(0));
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(1));
    assertEquals("E:1 ", myFragmentsContainer.getTextAt(1));
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(2));
    assertEquals("P:1", myFragmentsContainer.getTextAt(2));
  }

  public void testPresentation_SuiteStarted_DifferentResults() {
    mySuite.setStarted();
    final RTestUnitTestProxy passedTest1 = createTestProxy(mySuite);
    final RTestUnitTestProxy passedTest2 = createTestProxy(mySuite);
    final RTestUnitTestProxy passedTest3 = createTestProxy(mySuite);
    final RTestUnitTestProxy failedTest = createTestProxy(mySuite);
    final RTestUnitTestProxy errorTest1 = createTestProxy(mySuite);
    final RTestUnitTestProxy errorTest2 = createTestProxy(mySuite);
    final RTestUnitTestProxy inProgressTest = createTestProxy(mySuite);

    doRender(mySuite);
    assertEmpty(myFragmentsContainer.getFragments());

    passedTest1.setStarted();
    passedTest1.setFinished();
    passedTest2.setStarted();
    passedTest2.setFinished();
    passedTest3.setStarted();
    passedTest3.setFinished();

    failedTest.setStarted();
    failedTest.setTestFailed("", "", false);
    failedTest.setFinished();

    errorTest1.setStarted();
    errorTest1.setTestFailed("", "", true);
    errorTest1.setFinished();
    errorTest2.setStarted();
    errorTest2.setTestFailed("", "", true);
    errorTest2.setFinished();

    inProgressTest.setStarted();

    doRender(mySuite);
    assertFragmentsSize(3);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("F:1 ", myFragmentsContainer.getTextAt(0));
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(1));
    assertEquals("E:2 ", myFragmentsContainer.getTextAt(1));
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(2));
    assertEquals("P:3", myFragmentsContainer.getTextAt(2));
  }

  public void testPresentation_SuitePassed() {
    mySuite.setStarted();
    final RTestUnitTestProxy passedTest = createTestProxy(mySuite);
    final RTestUnitTestProxy failedTest = createTestProxy(mySuite);

    passedTest.setStarted();
    passedTest.setFinished();

    mySuite.setFinished();

    doRender(mySuite);
    assertFragmentsSize(1);
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("P:1", myFragmentsContainer.getTextAt(0));
  }

  public void testPresentation_SuiteFailed() {
    mySuite.setStarted();
    final RTestUnitTestProxy passedTest = createTestProxy(mySuite);
    final RTestUnitTestProxy failedTest = createTestProxy(mySuite);

    passedTest.setStarted();
    passedTest.setFinished();

    failedTest.setStarted();
    failedTest.setTestFailed("", "", false);
    failedTest.setFinished();

    mySuite.setFinished();

    doRender(mySuite);
    assertFragmentsSize(2);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("F:1 ", myFragmentsContainer.getTextAt(0));
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(1));
    assertEquals("P:1", myFragmentsContainer.getTextAt(1));
  }

  public void testPresentation_SuiteError() {
    mySuite.setStarted();
    final RTestUnitTestProxy passedTest = createTestProxy(mySuite);
    final RTestUnitTestProxy failedTest = createTestProxy(mySuite);

    passedTest.setStarted();
    passedTest.setFinished();

    failedTest.setStarted();
    failedTest.setTestFailed("", "", true);
    failedTest.setFinished();

    mySuite.setFinished();

    doRender(mySuite);
    assertFragmentsSize(2);
    assertEquals(TestsPresentationUtil.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("E:1 ", myFragmentsContainer.getTextAt(0));
    assertEquals(TestsPresentationUtil.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(1));
    assertEquals("P:1", myFragmentsContainer.getTextAt(1));
  }

  protected ColoredRenderer createRenderer(final RTestUnitTestProxy rTestUnitTestProxy,
                                           final UITestUtil.FragmentsContainer fragmentsContainer) {
    return new MyRenderer(rTestUnitTestProxy, fragmentsContainer);
  }

  protected ColumnInfo<RTestUnitTestProxy, String> createColumn() {
    return new ColumnResults();
  }


  private class MyRenderer extends ColumnResults.ResultsCellRenderer {
    private UITestUtil.FragmentsContainer myFragmentsContainer;

    private MyRenderer(final RTestUnitTestProxy proxy,
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