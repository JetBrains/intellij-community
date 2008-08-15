package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.plugins.ruby.support.UITestUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseColumnRenderingTest extends BaseRUnitTestsTestCase {
  protected ColumnInfo<RTestUnitTestProxy, String> myColumn;

  protected ColoredRenderer mySimpleTestRenderer;
  protected ColoredRenderer mySuiteRenderer;
  protected UITestUtil.FragmentsContainer myFragmentsContainer;

  protected abstract ColoredRenderer createRenderer(final RTestUnitTestProxy rTestUnitTestProxy,
                                                             final UITestUtil.FragmentsContainer fragmentsContainer);
  protected abstract ColumnInfo<RTestUnitTestProxy, String> createColumn();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myColumn = createColumn();    
    myFragmentsContainer = new UITestUtil.FragmentsContainer();

    mySimpleTestRenderer = createRenderer(mySimpleTest, myFragmentsContainer);
    mySuiteRenderer = createRenderer(mySuite, myFragmentsContainer);
  }

  protected void doRender(final RTestUnitTestProxy proxy) {
    if (proxy.isSuite()) {
      mySuiteRenderer.customizeCellRenderer(null, myColumn.valueOf(proxy), false, false, 0, 0);
    } else {
      mySimpleTestRenderer.customizeCellRenderer(null, myColumn.valueOf(proxy), false, false, 0, 0);
    }
  }

  protected void doRender(final RTestUnitTestProxy proxy, final int row) {
    if (proxy.isSuite()) {
      mySuiteRenderer.customizeCellRenderer(null, myColumn.valueOf(proxy), false, false, row, 0);
    } else {
      mySimpleTestRenderer.customizeCellRenderer(null, myColumn.valueOf(proxy), false, false, row, 0);
    }
  }

  protected void assertFragmentsSize(final int expectedSize) {
    assertEquals(expectedSize, myFragmentsContainer.getFragments().size());
  }
}
