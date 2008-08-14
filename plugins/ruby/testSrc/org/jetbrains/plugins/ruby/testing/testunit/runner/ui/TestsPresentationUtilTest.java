package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.support.UITestUtil;
import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitConsoleProperties;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

import javax.swing.*;
import java.text.NumberFormat;

/**
 * @author Roman Chernyatchik
 */
public class TestsPresentationUtilTest extends BaseRUnitTestsTestCase {
  @NonNls private static final String FAKE_TEST_NAME = "my test";
  private TestsPresentationUtilTest.MyRenderer myRenderer;
  private UITestUtil.FragmentsContainer myFragContainer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRenderer = new MyRenderer(false, new UITestUtil.FragmentsContainer());
    myFragContainer = myRenderer.getFragmentsContainer();
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
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_NewTestPaused() {
    //paused
    final MyRenderer pausedRenderer = new MyRenderer(true, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatTestProxy(mySimpleTest, pausedRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, pausedRenderer.getIcon());
    assertEquals(1, myFragContainer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Started() {
    //paused
    mySimpleTest.setStarted();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertEquals(1, myFragContainer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_StartedAndPaused() {
    //paused
    final MyRenderer pausedRenderer = new MyRenderer(true, myFragContainer = new UITestUtil.FragmentsContainer());

    mySimpleTest.setStarted();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, pausedRenderer);

    assertEquals(TestsProgressAnimator.PAUSED_ICON, pausedRenderer.getIcon());
    assertEquals(1, myFragContainer.getFragments().size());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatTestProxyTest_Failed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.FAILED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.FAILED_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Error() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.ERROR_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.ERROR_ICON, myRenderer.getIcon());
  }

  public void testFormatTestProxyTest_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals(FAKE_TEST_NAME, myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

    mySimpleTest.setFinished();
    TestsPresentationUtil.formatTestProxy(mySimpleTest, myRenderer);
    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
  }

  public void testFormatRootNodeWithChildren_Started() {
    mySimpleTest.setStarted();

    TestsPresentationUtil.formatRootNodeWithChildren(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Running tests...", myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithChildren_Failed() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);

    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results.", renderer1.getFragmentsContainer().getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getFragmentsContainer().getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer2);
    mySuite.setFinished();
    assertEquals(PoolOfTestIcons.FAILED_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results.", renderer1.getFragmentsContainer().getFragmentAt(0));
  }

  public void testFormatRootNodeWithChildren_Error() {
    final MyRenderer renderer1 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());

    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer1);

    assertEquals(PoolOfTestIcons.ERROR_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results.", renderer1.getFragmentsContainer().getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, renderer1.getFragmentsContainer().getAttribsAt(0));

    final MyRenderer renderer2 = new MyRenderer(false, myFragContainer = new UITestUtil.FragmentsContainer());
    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, renderer2);
    mySuite.setFinished();
    assertEquals(PoolOfTestIcons.ERROR_ICON, renderer1.getIcon());
    assertOneElement(renderer1.getFragmentsContainer().getFragments());
    assertEquals("Test Results.", renderer1.getFragmentsContainer().getFragmentAt(0));
  }

  public void testFormatRootNodeWithChildren_Passed() {
    mySuite.addChild(mySimpleTest);
    mySuite.setStarted();
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    mySuite.setFinished();

    TestsPresentationUtil.formatRootNodeWithChildren(mySuite, myRenderer);

    assertEquals(PoolOfTestIcons.PASSED_ICON, myRenderer.getIcon());
    assertOneElement(myRenderer.getFragmentsContainer().getFragments());
    assertEquals("Test Results.", myRenderer.getFragmentsContainer().getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myRenderer.getFragmentsContainer().getAttribsAt(0));
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
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated.", myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
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
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated.", myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));
  }

  public void testFormatRootNodeWithoutChildren() {
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("No Test Results.", myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Started() {
    mySimpleTest.setStarted();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertIsAnimatorProgressIcon(myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Instantiating tests...", myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.NOT_RAN, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("No tests were found.", myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.ERROR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

  }

  public void testFormatRootNodeWithoutChildren_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    TestsPresentationUtil.formatRootNodeWithoutChildren(mySimpleTest, myRenderer);

    assertEquals(PoolOfTestIcons.TERMINATED_ICON, myRenderer.getIcon());
    assertOneElement(myFragContainer.getFragments());
    assertEquals("Terminated.", myFragContainer.getFragmentAt(0));
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, myFragContainer.getAttribsAt(0));

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

  public void testGetTestStatusPresentation_NotRun() {
    assertEquals("Not run", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_Progress() {
    mySimpleTest.setStarted();
    assertEquals("Running...", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertEquals("Passed", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_Failed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("Assertion failed", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
    mySimpleTest.setFinished();
    assertEquals("Assertion failed", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    assertEquals("Error", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
    mySimpleTest.setFinished();
    assertEquals("Error", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  public void testGetTestStatusPresentation_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("Terminated", TestsPresentationUtil.getTestStatusPresentation(mySimpleTest));
  }

  //TODO
  //public void testGetSuiteStatusPresentation_NotRun() {
  //   fail("Not implemented");
  //}
  //
  //public void testGetSuiteStatusPresentation_Progress() {
  //   fail("Not implemented");
  //}
  //
  //public void testGetSuiteStatusPresentation_Passed() {
  //   fail("Not implemented");
  //}
  //
  //public void testGetSuiteStatusPresentation_Failed() {
  //   fail("Not implemented");
  //}
  //
  //public void testGetSuiteStatusPresentation_TestError() {
  //   fail("Not implemented");
  //}
  //
  //public void testGetSuiteStatusPresentation_Terminated() {
  //   fail("Not implemented");
  //}

  public void testGetDurationPresentation_NotRun() {
    assertEquals("<unknown>", TestsPresentationUtil.getDurationPresentation(mySimpleTest));
  }

  public void testGetDurationPresentation_Progress() {
    mySimpleTest.setStarted();
    assertEquals("<unknown>", TestsPresentationUtil.getDurationPresentation(mySimpleTest));
  }

  public void testGetDurationPresentation_Passed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertEquals("<unknown>", TestsPresentationUtil.getDurationPresentation(mySimpleTest));
  }

  public void testGetDurationPresentation_Failed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<unknown>", TestsPresentationUtil.getDurationPresentation(mySimpleTest));
    mySimpleTest.setFinished();
    assertEquals("<unknown>", TestsPresentationUtil.getDurationPresentation(mySimpleTest));
  }


  public void testGetDurationPresentation_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    assertEquals("<unknown>", TestsPresentationUtil.getDurationPresentation(mySimpleTest));
    mySimpleTest.setFinished();
    assertEquals("<unknown>", TestsPresentationUtil.getDurationPresentation(mySimpleTest));
  }

  public void testGetDurationPresentation_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("<unknown>", TestsPresentationUtil.getDurationPresentation(mySimpleTest));
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
    private UITestUtil.FragmentsContainer myFragmentsContainer;

    public MyRenderer(final boolean isPaused,
                      final UITestUtil.FragmentsContainer fragmentsContainer) {
      super(new RTestUnitConsoleProperties(createRTestsRunConfiguration()) {
        @Override
        public boolean isPaused() {
          return isPaused;
        }
      });
      myFragmentsContainer = fragmentsContainer;
    }

    @Override
    public void append(@NotNull @Nls final String fragment,
                       @NotNull final SimpleTextAttributes attributes,
                       final boolean isMainText) {
      myFragmentsContainer.append(fragment, attributes);
    }

    public UITestUtil.FragmentsContainer getFragmentsContainer() {
      return myFragmentsContainer;
    }
  }
}
