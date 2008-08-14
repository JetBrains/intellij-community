package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.support.UITestUtil;

/**
 * @author Roman Chernyatchik
 */
public class ColumnResultsTest extends BaseRUnitTestsTestCase {
  private ColumnResults myColumnResults;
  private MyRenderer mySimpleTestRenderer;
  private UITestUtil.FragmentsContainer myFragmentsContainer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myColumnResults = new ColumnResults();
    myFragmentsContainer = new UITestUtil.FragmentsContainer();
    mySimpleTestRenderer = createRenderer(mySimpleTest, myFragmentsContainer);
  }

  public void testValueOf_TestNotRun() {
    doRender(mySimpleTest);

    assertEquals(1, myFragmentsContainer.getFragments().size());
    assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Not run", myFragmentsContainer.getFragmentAt(0));
  }

  public void testValueOf_TestInProgress() {
    mySimpleTest.setStarted();

    doRender(mySimpleTest);
    assertEquals(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Running...", myFragmentsContainer.getFragmentAt(0));
  }

  public void testValueOf_TestFailure() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);

    doRender(mySimpleTest);
    assertEquals(ColumnResults.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Assertion failed", myFragmentsContainer.getFragmentAt(0));
  }

  public void testValueOf_TestPassed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    doRender(mySimpleTest);
    assertEquals(ColumnResults.PASSED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Passed", myFragmentsContainer.getFragmentAt(0));
  }

  public void testValueOf_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);

    doRender(mySimpleTest);
    assertEquals(ColumnResults.DEFFECT_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Error", myFragmentsContainer.getFragmentAt(0));
  }

  public void testValueOf_TestTerminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("Terminated", myColumnResults.valueOf(mySimpleTest));

    doRender(mySimpleTest);
    assertEquals(ColumnResults.TERMINATED_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Terminated", myFragmentsContainer.getFragmentAt(0));
  }

  private MyRenderer createRenderer(final RTestUnitTestProxy rTestUnitTestProxy,
                                    final UITestUtil.FragmentsContainer fragmentsContainer) {
    return new MyRenderer(rTestUnitTestProxy, fragmentsContainer);
  }


  private void doRender(final RTestUnitTestProxy rTestUnitTestProxy) {
    mySimpleTestRenderer.customizeCellRenderer(null, myColumnResults.valueOf(rTestUnitTestProxy), false, false, 0, 0);
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