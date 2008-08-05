package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitConsoleProperties;

import javax.swing.*;
import java.util.ArrayList;
import java.text.NumberFormat;

/**
 * @author Roman Chernyatchik
 */
public class TestsPresentationUtilTest extends BaseRUnitTestsTestCase {
  @NonNls private static final String FAKE_TEST_NAME = "my test";
  private TestsPresentationUtilTest.MyRenderer myRenderer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRenderer = new MyRenderer(false);
  }

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
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals(FAKE_TEST_NAME, myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_NewTestPaused() {
    //paused
    final MyRenderer pausedRenderer = new MyRenderer(true);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, pausedRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, pausedRenderer.getIcon());
    assertEquals(1, pausedRenderer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, pausedRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, pausedRenderer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Started() {
    //paused
    mySimpleTest.setStarted();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertEquals(1, myRenderer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_StartedAndPaused() {
    //paused
    final MyRenderer pausedRenderer = new MyRenderer(true);

    mySimpleTest.setStarted();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, pausedRenderer);

    assertEquals(TestsProgressAnimator.PAUSED_ICON, pausedRenderer.getIcon());
    assertEquals(1, pausedRenderer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, pausedRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, pausedRenderer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals(FAKE_TEST_NAME, myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Failed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "");
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.FAILED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals(FAKE_TEST_NAME, myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.FAILED_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals(FAKE_TEST_NAME, myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
  }

  public void testFormatRootNodeWithChildren_Started() {
    mySimpleTest.setStarted();

    TestsPresentationUtil.formatRootNodeWithChildren(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals("Running tests...", myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Failed() {
    final MyRenderer renderer1 = new MyRenderer(false);

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "");
    mySimpleTest.setFinished();
    mySuite.setTestFailed("", "");

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);

    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("Test Results.", renderer1.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false);
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer2);
    mySuite.setFinished();
    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragments());
    assertEquals("Test Results.", renderer1.getFragmentAt(0));
  }

  public void testFormatRootNodeWithChildren_Passed() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals("Test Results.", myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Terminated() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySuite.setTerminated();
    // terminated
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals("Terminated.", myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_TerminatedAndFinished() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySuite.setTerminated();
    mySuite.setFinished();

    // terminated and finished
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);
    mySuite.setFinished();
    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals("Terminated.", myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithoutChildren() {
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals("No Test Results.", myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, myRenderer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Started() {
    mySimpleTest.setStarted();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals("Instantiating tests...", myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals("No tests were found.", myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, myRenderer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragments());
    assertEquals("Terminated.", myRenderer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getAttribsAt(0));

  }

  public void testGetPresentableName() {
    //Test unit examples
    assertProxyPresentation("testFirst", "MyRubyTest1", "MyRubyTest1.testFirst");
    assertProxyPresentation("MyRubyTest1.testFirst", "/some/path/on/my/comp", "MyRubyTest1.testFirst");

    //Spec example
    assertProxyPresentation("should be beautifull", "World", "should be beautifull");

    //Common example
    assertProxyPresentation("some phrase", "Begin of", "Begin of some phrase");


    //Bound examples
    assertEquals("suite without parent",
                 TestsPresentationUtil.getPresentableName(createSuiteProxy("suite without parent")));
    assertEquals("test without parent",
                 TestsPresentationUtil.getPresentableName(createTestProxy("test without parent")));
    assertEquals("with spaces",
                 TestsPresentationUtil.getPresentableName(createSuiteProxy("    with spaces  ")));

  }

  private void assertProxyPresentation(final String expectedPresentation, final String parentName,
                                       final String childName) {
    assertEquals(expectedPresentation,
                 TestsPresentationUtil.getPresentableName(createChildSuiteOfParentSuite(parentName, childName)));
    assertEquals(expectedPresentation,
                 TestsPresentationUtil.getPresentableName(createChildTestOfSuite(parentName, childName)));
  }

  protected RTestUnitTestProxy createChildSuiteOfParentSuite(final String parentName, final String childName) {
    final RTestUnitTestProxy parentSuite = createSuiteProxy(parentName);
    final RTestUnitTestProxy childSuite = createTestProxy(childName);
    parentSuite.addChild(childSuite);

    return childSuite;
  }

  protected RTestUnitTestProxy createChildTestOfSuite(final String suiteName, final String childName) {
    final RTestUnitTestProxy suiteProxy = createSuiteProxy(suiteName);
    final RTestUnitTestProxy test = createTestProxy(childName);
    suiteProxy.addChild(test);
    return test;
  }

  protected RTestUnitTestProxy createTestProxy() {
    return createTestProxy(FAKE_TEST_NAME);
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
