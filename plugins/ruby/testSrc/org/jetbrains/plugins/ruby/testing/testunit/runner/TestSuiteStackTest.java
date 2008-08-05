package org.jetbrains.plugins.ruby.testing.testunit.runner;

import org.jetbrains.plugins.ruby.support.AssertionUtil;
import org.jetbrains.plugins.ruby.support.EmptyStackExceptionCase;

/**
 * @author Roman Chernyatchik
 */
public class TestSuiteStackTest extends BaseRUnitTestsTestCase {
  private TestSuiteStack myTestSuiteStack;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myTestSuiteStack = new TestSuiteStack();
  }

  public void testPushSuite() {
    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(1, myTestSuiteStack.getStackSize());
    assertEquals(mySuite, myTestSuiteStack.getCurrentSuite());

    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(2, myTestSuiteStack.getStackSize());
    assertEquals(mySuite, myTestSuiteStack.getCurrentSuite());

    final RTestUnitTestProxy newSuite = createSuiteProxy();
    myTestSuiteStack.pushSuite(newSuite);
    assertEquals(3, myTestSuiteStack.getStackSize());
    assertEquals(newSuite, myTestSuiteStack.getCurrentSuite());
  }

  public void testGetStackSize() {
    assertEquals(0, myTestSuiteStack.getStackSize());

    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(1, myTestSuiteStack.getStackSize());

    myTestSuiteStack.popSuite(mySuite.getName());
    assertEquals(0, myTestSuiteStack.getStackSize());
  }

  public void testGetCurrentSuite() {
    assertNull(myTestSuiteStack.getCurrentSuite());

    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(mySuite, myTestSuiteStack.getCurrentSuite());
  }

  public void testPopSuite() {
    final String suiteName = mySuite.getName();

    AssertionUtil.assertException(new EmptyStackExceptionCase() {
      public void tryClosure() {
        myTestSuiteStack.popSuite("some suite");
      }
    });

    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(mySuite, myTestSuiteStack.popSuite(suiteName));
    assertEquals(0, myTestSuiteStack.getStackSize());
  }

  public void testGetSuitePath() {
    assertEmpty(myTestSuiteStack.getSuitePath());

    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.pushSuite(createSuiteProxy("3"));

    assertSameElements(myTestSuiteStack.getSuitePath(), "1", "2", "3");
  }

  public void testGetSuitePathPresentation() {
    assertEquals("empty", myTestSuiteStack.getSuitePathPresentation());

    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.pushSuite(createSuiteProxy("3"));

    assertEquals("[1]->[2]->[3]", myTestSuiteStack.getSuitePathPresentation());    
  }

  public void testClear() {
    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.pushSuite(createSuiteProxy("3"));
    myTestSuiteStack.clear();

    assertEquals(0, myTestSuiteStack.getStackSize());
  }

  public void testIsEmpty() {
    assertTrue(myTestSuiteStack.isEmpty());

    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    assertFalse(myTestSuiteStack.isEmpty());

    myTestSuiteStack.popSuite("1");
    assertTrue(myTestSuiteStack.isEmpty());

    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.clear();
    assertTrue(myTestSuiteStack.isEmpty());    
  }
}
