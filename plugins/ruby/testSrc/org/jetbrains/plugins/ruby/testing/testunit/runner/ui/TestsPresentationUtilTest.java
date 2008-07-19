package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.model.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.properties.RTestUnitConsoleProperties;

import javax.swing.*;
import java.util.ArrayList;
import java.text.NumberFormat;

/**
 * @author Roman Chernyatchik
 */
public class TestsPresentationUtilTest extends BaseRUnitTestsTestCase {
  @NonNls private static final String FAKE_TEST_NAME = "my test";

  public void testProgressText() {
    assertEquals("Running: 10 of 1  Failed: 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 1, 10, 1));
    assertEquals("Running: 10 of 1  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 0, 1, 10, 0));
    //here number format is platform-dependent
    assertEquals("Done: 10 of 0  Failed: 1  (" + NumberFormat.getInstance().format((double)5/1000.0) + " s)  ",
                 TestsPresentationUtil.getProgressStatus_Text(0, 5, 0, 10, 1));
    assertEquals("Done: 10 of 1  (0.0 s)  ",
                 TestsPresentationUtil.getProgressStatus_Text(5, 5, 1, 10, 0));
  }

  public void testFormatTestProxyTest_NewTest() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(false);
    TestsPresentationUtil.formatTestProxy(proxy1, renderer1);

    assertEquals(PoolOfTestIcons.NOT_RAN, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals(FAKE_TEST_NAME, renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_NewTestPaused() {
    //paused
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(true);
    TestsPresentationUtil.formatTestProxy(proxy1, renderer1);

    assertEquals(PoolOfTestIcons.NOT_RAN, renderer1.getIcon());
    assertEquals(1, renderer1.getFragments().size());
    assertEquals(FAKE_TEST_NAME, renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Started() {
    //paused
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(false);
    proxy1.setStarted();
    TestsPresentationUtil.formatTestProxy(proxy1, renderer1);

    assertIsAnimatorProgressIcon(renderer1.getIcon());
    assertEquals(1, renderer1.getFragments().size());
    assertEquals(FAKE_TEST_NAME, renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_StartedAndPaused() {
    //paused
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(true);
    proxy1.setStarted();
    TestsPresentationUtil.formatTestProxy(proxy1, renderer1);

    assertEquals(TestsProgressAnimator.PAUSED_ICON, renderer1.getIcon());
    assertEquals(1, renderer1.getFragments().size());
    assertEquals(FAKE_TEST_NAME, renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Passed() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(false);
    proxy1.setStarted();
    proxy1.setFinished();
    TestsPresentationUtil.formatTestProxy(proxy1, renderer1);

    assertEquals(PoolOfTestIcons.PASSED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals(FAKE_TEST_NAME, renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Failed() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(false);
    proxy1.setStarted();
    proxy1.setFailed();
    TestsPresentationUtil.formatTestProxy(proxy1, renderer1);

    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals(FAKE_TEST_NAME, renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));

    proxy1.setFinished();
    TestsPresentationUtil.formatTestProxy(proxy1, renderer1);
    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
  }

  public void testFormatRootNodeWithChildren_Started() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(false);

    proxy1.setStarted();

    TestsPresentationUtil.formatRootNodeWithChildren(proxy1, renderer1);

    assertIsAnimatorProgressIcon(renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("Running tests...", renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Failed() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final RTestUnitTestProxy child = createTestProxy();
    proxy1.addChild(child);
    final MyRenderer renderer1 = new MyRenderer(false);
    proxy1.setStarted();
    child.setStarted();
    child.setFailed();
    child.setFinished();
    proxy1.setFailed();

    TestsPresentationUtil.formatRootNodeWithChildren(proxy1, renderer1);

    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("Test Results", renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false);
    TestsPresentationUtil.formatRootNodeWithChildren(proxy1, renderer2);
    proxy1.setFinished();
    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("Test Results", renderer1.getFragmentAt(0));
  }

  public void testFormatRootNodeWithChildren_Passed() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final RTestUnitTestProxy child = createTestProxy();
    proxy1.addChild(child);
    final MyRenderer renderer1 = new MyRenderer(false);
    proxy1.setStarted();
    child.setStarted();
    child.setFinished();
    proxy1.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(proxy1, renderer1);

    assertEquals(PoolOfTestIcons.PASSED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("Test Results", renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));
  }

  public void testFormatRootNodeWithoutChildren() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(false);

    TestsPresentationUtil.formatRootNodeWithoutChildren(proxy1, renderer1);

    assertEquals(PoolOfTestIcons.NOT_RAN, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("No Test Results.", renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, renderer1.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Started() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(false);
    proxy1.setStarted();
    TestsPresentationUtil.formatRootNodeWithoutChildren(proxy1, renderer1);

    assertIsAnimatorProgressIcon(renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("Instantiating tests...", renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Passed() {
    final RTestUnitTestProxy proxy1 = createTestProxy();
    final MyRenderer renderer1 = new MyRenderer(false);

    proxy1.setStarted();
    proxy1.setFinished();
    TestsPresentationUtil.formatRootNodeWithoutChildren(proxy1, renderer1);

    assertEquals(PoolOfTestIcons.PASSED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("All Tests Passed.", renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));

  }

  protected RTestUnitTestProxy createTestProxy() {
    return new RTestUnitTestProxy(FAKE_TEST_NAME);
  }

  private void assertIsAnimatorProgressIcon(final Icon icon) {
    for (Icon frame : TestsProgressAnimator.FRAMES) {
      if (icon == frame) {
        return;
      }
    }

    fail("Icon isn't an Animator progress frame");
  }

  private class MyRenderer extends RTestUnitTestTreeRenderer {
    private ListOfFragments myFragments;

    public MyRenderer(final boolean isPaused) {
      super(new RTestUnitConsoleProperties(createRTestsRunConfiguration()) {
        @Override
        public boolean isPaused() {
          return isPaused;
        }
      });

      myFragments = new ListOfFragments();
    }

    @Override
    public void append(@NotNull @Nls final String fragment,
                       @NotNull final SimpleTextAttributes attributes,
                       final boolean isMainText) {
      myFragments.add(fragment, attributes);
    }

    public ListOfFragments getFragments() {
      return myFragments;
    }

    public String getFragmentAt(final int index) {
      return myFragments.get(index).first;
    }

    public SimpleTextAttributes getAttribsAt(final int index) {
      return myFragments.get(index).second;
    }
  }

  private static class ListOfFragments extends ArrayList<Pair<String, SimpleTextAttributes>> {
    public void add(@NotNull @Nls final String fragment, @NotNull final SimpleTextAttributes attributes) {
      add(new Pair<String, SimpleTextAttributes>(fragment, attributes));
    }
  }
}
