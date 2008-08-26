package org.jetbrains.plugins.ruby.testing.sm.runner.ui.statistics;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.plugins.ruby.support.UITestUtil;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
 */
public class ColumnTestTest extends BaseColumnRenderingTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myColumn = new ColumnTest();
  }

  public void testValueOf_Test() {
    assertEquals("test", myColumn.valueOf(createTestProxy("test")));

    final SMTestProxy test = createTestProxy("test of suite", mySuite);
    assertEquals("test of suite", myColumn.valueOf(test));
  }

  public void testValueOf_TestNameCollapsing() {
    assertEquals("test", myColumn.valueOf(createTestProxy("test")));

    final SMTestProxy suiteProxy = createSuiteProxy("MySuite");
    assertEquals("test of suite", myColumn.valueOf(createTestProxy("MySuite.test of suite", suiteProxy)));
    assertEquals("test of suite", myColumn.valueOf(createTestProxy("MySuite test of suite", suiteProxy)));
    assertEquals("Not MySuite test of suite", myColumn.valueOf(createTestProxy("Not MySuite test of suite", suiteProxy)));
  }

  public void testValueOf_Suite() {
    final SMTestProxy suite = createSuiteProxy("my suite", mySuite);
    createTestProxy("test", suite);
    assertEquals("my suite", myColumn.valueOf(suite));
  }

  public void testValueOf_SuiteNameCollapsing() {
    final SMTestProxy suiteProxy = createSuiteProxy("MySuite");
    assertEquals("child suite", myColumn.valueOf(createSuiteProxy("MySuite.child suite", suiteProxy)));
    assertEquals("child suite", myColumn.valueOf(createSuiteProxy("MySuite child suite", suiteProxy)));
    assertEquals("Not MySuite child suite", myColumn.valueOf(createSuiteProxy("Not MySuite child suite", suiteProxy)));
  }

  public void testTotal_Test() {
    mySuite.addChild(mySimpleTest);

    doRender(mySimpleTest, 0);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(mySimpleTest.getPresentableName(), myFragmentsContainer.getTextAt(0));

    myFragmentsContainer.clear();
    doRender(mySimpleTest, 1);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(mySimpleTest.getPresentableName(), myFragmentsContainer.getTextAt(0));
  }

  public void testTotal_RegularSuite() {
    doRender(mySuite, 1);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals(mySuite.getPresentableName(), myFragmentsContainer.getTextAt(0));
  }

  public void testTotal_TotalSuite() {
    doRender(mySuite, 0);
    assertFragmentsSize(1);
    assertEquals(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, myFragmentsContainer.getAttribsAt(0));
    assertEquals("Total:", myFragmentsContainer.getTextAt(0));
  }

  protected ColoredRenderer createRenderer(final SMTestProxy proxy,
                                           final UITestUtil.FragmentsContainer fragmentsContainer) {
    return new MyRenderer(proxy, fragmentsContainer);
  }

  protected ColumnInfo<SMTestProxy, String> createColumn() {
    return new ColumnTest();
  }

  private class MyRenderer extends ColumnTest.TestsCellRenderer {
    private UITestUtil.FragmentsContainer myFragmentsContainer;

    public MyRenderer(final SMTestProxy proxy,
                      final UITestUtil.FragmentsContainer fragmentsContainer) {
      super(proxy);
      myFragmentsContainer = fragmentsContainer;
    }

    @Override
    public void append(@NotNull final String fragment,
                       @NotNull final SimpleTextAttributes attributes,
                       final boolean isMainText) {
      myFragmentsContainer.append(fragment, attributes);
    }
  }
}
