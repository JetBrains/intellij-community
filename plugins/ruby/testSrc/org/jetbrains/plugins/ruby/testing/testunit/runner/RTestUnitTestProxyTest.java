package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.testframework.Filter;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitTestProxyTest extends BaseRUnitTestsTestCase {

  public void testTestInstance() {
    mySimpleTest = createTestProxy("newTest");

    assertEquals("newTest", mySimpleTest.getName());
    assertEquals("newTest", mySimpleTest.toString());

    assertEmpty(mySimpleTest.getChildren());
    assertTrue(mySimpleTest.isLeaf());
    assertNull(mySimpleTest.getParent());

    assertFalse(mySimpleTest.wasLaunched());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testGetName() {
    mySimpleTest = createTestProxy("newTest");
    assertEquals("newTest", mySimpleTest.getName());

    mySuite = createSuiteProxy("newSuite");
    assertEquals("newSuite", mySuite.getName());

    mySuite.setParent(mySimpleTest);
    assertEquals("newTest", mySimpleTest.getName());
  }

  public void testGetName_trim() {
    mySimpleTest = createTestProxy(" newTest ");
    assertEquals(" newTest ", mySimpleTest.getName());
  }

  public void testSuiteInstance() {
    mySuite = createSuiteProxy("newSuite");

    assertEquals("newSuite", mySuite.getName());
    assertEquals("newSuite", mySuite.toString());

    assertEmpty(mySuite.getChildren());
    assertTrue(mySuite.isLeaf());
    assertNull(mySuite.getParent());

    assertFalse(mySuite.wasLaunched());
    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());

    mySuite.addChild(mySimpleTest);
    assertEquals("newSuite", mySuite.getName());
    assertEquals("newSuite", mySuite.toString());
    assertSameElements(mySuite.getChildren(), mySimpleTest);
    assertFalse(mySuite.isLeaf());
  }

  public void testIsRoot() {
    final RTestUnitTestProxy rootTest = createTestProxy("root");
    assertTrue(rootTest.isRoot());

    rootTest.addChild(mySimpleTest);

    assertFalse(mySimpleTest.isRoot());
  }

  public void testTestStarted() {
    mySimpleTest.setStarted();

    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isInProgress());

    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestStarted_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    assertTrue(mySuite.wasLaunched());
    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertFalse(mySimpleTest.wasLaunched());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());

    mySimpleTest.setStarted();

    assertTrue(mySuite.wasLaunched());
    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertTrue(mySimpleTest.wasLaunched());
    assertTrue(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestFinished() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySimpleTest.wasLaunched());

    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestFinished_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
    assertFalse(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());
  }

  public void testTestFinished_InSuite_WrongOrder() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertFalse(mySuite.isDefect());

    assertTrue(mySimpleTest.isInProgress());
    assertFalse(mySimpleTest.isDefect());
  }

  public void testTestFailed() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "");

    assertTrue(mySimpleTest.wasLaunched());
    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.isDefect());

    mySimpleTest.setFinished();

    assertTrue(mySimpleTest.wasLaunched());
    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.isDefect());
  }

  public void testTestFailed_InSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);

    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "");

    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());
    assertFalse(mySimpleTest.isInProgress());
    assertTrue(mySimpleTest.isDefect());

    mySimpleTest.setFinished();

    assertTrue(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());

    mySuite.setFinished();

    assertFalse(mySuite.isInProgress());
    assertTrue(mySuite.isDefect());
  }

  public void testMagnitude() {
    assertEquals(RTestUnitTestProxy.NOT_RUN_INDEX, mySuite.getMagnitude());

    final RTestUnitTestProxy passedTest = createTestProxy("passed");
    final RTestUnitTestProxy failedTest = createTestProxy("failed");
    mySuite.addChild(passedTest);
    mySuite.addChild(failedTest);

    assertEquals(RTestUnitTestProxy.NOT_RUN_INDEX, mySuite.getMagnitude());
    assertEquals(RTestUnitTestProxy.NOT_RUN_INDEX, passedTest.getMagnitude());
    assertEquals(RTestUnitTestProxy.NOT_RUN_INDEX, failedTest.getMagnitude());

    mySuite.setStarted();
    assertEquals(RTestUnitTestProxy.RUNNING_INDEX, mySuite.getMagnitude());
    assertEquals(RTestUnitTestProxy.NOT_RUN_INDEX, passedTest.getMagnitude());
    assertEquals(RTestUnitTestProxy.NOT_RUN_INDEX, failedTest.getMagnitude());

    passedTest.setStarted();
    assertEquals(RTestUnitTestProxy.RUNNING_INDEX, mySuite.getMagnitude());
    assertEquals(RTestUnitTestProxy.RUNNING_INDEX, passedTest.getMagnitude());
    assertEquals(RTestUnitTestProxy.NOT_RUN_INDEX, failedTest.getMagnitude());

    passedTest.setFinished();
    assertEquals(RTestUnitTestProxy.RUNNING_INDEX, mySuite.getMagnitude());
    assertEquals(RTestUnitTestProxy.PASSED_INDEX, passedTest.getMagnitude());
    assertEquals(RTestUnitTestProxy.NOT_RUN_INDEX, failedTest.getMagnitude());

    failedTest.setStarted();
    assertEquals(RTestUnitTestProxy.RUNNING_INDEX, mySuite.getMagnitude());
    assertEquals(RTestUnitTestProxy.PASSED_INDEX, passedTest.getMagnitude());
    assertEquals(RTestUnitTestProxy.RUNNING_INDEX, failedTest.getMagnitude());

    failedTest.setTestFailed("", "");
    assertEquals(RTestUnitTestProxy.RUNNING_INDEX, mySuite.getMagnitude());
    assertEquals(RTestUnitTestProxy.PASSED_INDEX, passedTest.getMagnitude());
    assertEquals(RTestUnitTestProxy.FAILED_INDEX, failedTest.getMagnitude());

    mySuite.setFinished();
    assertEquals(RTestUnitTestProxy.FAILED_INDEX, mySuite.getMagnitude());
    assertEquals(RTestUnitTestProxy.PASSED_INDEX, passedTest.getMagnitude());
    assertEquals(RTestUnitTestProxy.FAILED_INDEX, failedTest.getMagnitude());

    final RTestUnitTestProxy noTests = createSuiteProxy("failedSuite");
    noTests.setStarted();
    noTests.setFinished();
    assertEquals(RTestUnitTestProxy.FAILED_INDEX, noTests.getMagnitude());

    final RTestUnitTestProxy passedSuite = createSuiteProxy("passedSuite");
    final RTestUnitTestProxy passedSuiteTest = createTestProxy("test");
    passedSuite.setStarted();
    passedSuite.addChild(passedSuiteTest);
    passedSuiteTest.setStarted();
    passedSuiteTest.setFinished();
    passedSuite.setFinished();
    assertEquals(RTestUnitTestProxy.PASSED_INDEX, passedSuite.getMagnitude());
  }

  public void testLocation() {
    assertNull(mySuite.getLocation(getProject()));

    mySuite.addChild(mySimpleTest);

    assertNull(mySuite.getLocation(getProject()));
    assertNull(mySimpleTest.getLocation(getProject()));
  }

  public void testNavigatable() {
    assertNull(mySuite.getDescriptor(null));

    mySuite.addChild(mySimpleTest);

    assertNull(mySuite.getDescriptor(null));
    assertNull(mySimpleTest.getDescriptor(null));
  }

  public void testShouldRun_Test() {
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_Suite() {
    assertTrue(mySuite.shouldRun());

    mySuite.addChild(mySimpleTest);
    assertTrue(mySuite.shouldRun());

    mySimpleTest.setStarted();
    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_StartedTest() {
    mySimpleTest.setStarted();
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_StartedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    assertTrue(mySuite.shouldRun());

    mySimpleTest.setStarted();
    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_FailedTest() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "");
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_FailedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "");

    assertTrue(mySuite.shouldRun());
  }

  public void testShouldRun_PassedTest() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertTrue(mySimpleTest.shouldRun());
  }

  public void testShouldRun_PassedSuite() {
    mySuite.setStarted();
    mySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();

    assertTrue(mySuite.shouldRun());
  }

  public void testFilter() {
    assertEmpty(mySuite.getChildren(Filter.NO_FILTER));
    assertEmpty(mySuite.getChildren(null));

    mySuite.addChild(mySimpleTest);

    assertEquals(1, mySuite.getChildren(Filter.NO_FILTER).size());
    assertEquals(1, mySuite.getChildren(null).size());
  }

  public void testGetAllTests() {
    assertOneElement(mySuite.getAllTests());

    final RTestUnitTestProxy suite1 = createTestProxy("newTest");
    mySuite.addChild(suite1);
    final RTestUnitTestProxy test11 = createTestProxy("newTest");
    suite1.addChild(test11);

    final RTestUnitTestProxy suite2 = createTestProxy("newTest");
    suite1.addChild(suite2);
    final RTestUnitTestProxy test21 = createTestProxy("newTest");
    suite2.addChild(test21);
    final RTestUnitTestProxy test22 = createTestProxy("newTest");
    suite2.addChild(test22);

    assertEquals(6, mySuite.getAllTests().size());
    assertEquals(5, suite1.getAllTests().size());
    assertEquals(3, suite2.getAllTests().size());
    assertOneElement(test11.getAllTests());
    assertOneElement(test21.getAllTests());
  }

  public void testIsSuite() {
    assertFalse(mySimpleTest.isSuite());

    mySimpleTest.setStarted();
    assertFalse(mySimpleTest.isSuite());

    final RTestUnitTestProxy suite = mySuite;
    assertTrue(suite.isSuite());

    suite.setStarted();
    assertTrue(suite.isSuite());
  }
}
