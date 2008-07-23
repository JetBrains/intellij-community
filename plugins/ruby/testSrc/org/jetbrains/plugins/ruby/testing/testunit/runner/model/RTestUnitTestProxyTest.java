package org.jetbrains.plugins.ruby.testing.testunit.runner.model;

import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import com.intellij.execution.testframework.Filter;

/**
 * @author Roman Chernyatchik
 */
public class RTestUnitTestProxyTest extends BaseRUnitTestsTestCase {
  public void testNewInstance() {
    final RTestUnitTestProxy proxy = createTestProxy("newTest");

    assertEquals("newTest", proxy.getName());
    assertEmpty(proxy.getChildren());
    assertTrue(proxy.isLeaf());
    assertNull(proxy.getParent());

    assertTrue(!proxy.wasRun());
    assertTrue(!proxy.isInProgress());
    assertTrue(!proxy.isDefect());
  }

  public void testIsRoot() {
    final RTestUnitTestProxy rootTest = createTestProxy("root");
    assertTrue(rootTest.isRoot());

    final RTestUnitTestProxy childTest = createTestProxy("child");
    rootTest.addChild(childTest);

    assertFalse(childTest.isRoot());
  }

  public void testProxyStarted() {
    final RTestUnitTestProxy proxy = createTestProxy("newTest");

    proxy.setStarted();

    assertTrue(proxy.wasRun());
    assertTrue(proxy.isInProgress());

    assertTrue(!proxy.isDefect());
  }

  public void testProxyStarted_WithParent() {
    myRoot.setStarted();

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    assertTrue(myRoot.wasRun());
    assertTrue(myRoot.isInProgress());
    assertTrue(!myRoot.isDefect());
    assertTrue(!proxy.wasRun());
    assertTrue(!proxy.isInProgress());
    assertTrue(!proxy.isDefect());

    proxy.setStarted();

    assertTrue(myRoot.wasRun());
    assertTrue(myRoot.isInProgress());
    assertTrue(!myRoot.isDefect());
    assertTrue(proxy.wasRun());
    assertTrue(proxy.isInProgress());
    assertTrue(!proxy.isDefect());
  }

  public void testProxyFinished() {
    final RTestUnitTestProxy proxy = createTestProxy("newTest");

    proxy.setStarted();
    proxy.setFinished();

    assertTrue(proxy.wasRun());

    assertTrue(!proxy.isInProgress());
    assertTrue(!proxy.isDefect());
  }

  public void testProxyFinished_WithParent_WrongOrder() {
    myRoot.setStarted();

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    proxy.setStarted();
    myRoot.setFinished();

    assertTrue(!myRoot.isInProgress());
    assertTrue(!myRoot.isDefect());
    assertTrue(proxy.isInProgress());
    assertTrue(!proxy.isDefect());
  }

  public void testProxyFinished_WithParent() {
    myRoot.setStarted();

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    proxy.setStarted();
    proxy.setFinished();

    assertTrue(myRoot.isInProgress());
    assertTrue(!myRoot.isDefect());
    assertTrue(!proxy.isInProgress());
    assertTrue(!proxy.isDefect());

    myRoot.setFinished();

    assertTrue(!myRoot.isInProgress());
    assertTrue(!myRoot.isDefect());
  }

  public void testProxyFailed() {
    final RTestUnitTestProxy proxy = createTestProxy("newTest");

    proxy.setStarted();
    proxy.setFailed();

    assertTrue(proxy.wasRun());
    assertTrue(!proxy.isInProgress());
    assertTrue(proxy.isDefect());

    proxy.setFinished();

    assertTrue(proxy.wasRun());
    assertTrue(!proxy.isInProgress());
    assertTrue(proxy.isDefect());
  }

  public void testProxyFailed_WithParent() {
    myRoot.setStarted();

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    proxy.setStarted();
    proxy.setFailed();

    assertTrue(myRoot.isInProgress());
    assertTrue(myRoot.isDefect());
    assertTrue(!proxy.isInProgress());
    assertTrue(proxy.isDefect());

    proxy.setFinished();

    assertTrue(myRoot.isInProgress());
    assertTrue(myRoot.isDefect());

    myRoot.setFinished();

    assertTrue(!myRoot.isInProgress());
    assertTrue(myRoot.isDefect());
  }

  public void testMagnitude() {
    assertEquals(0, myRoot.getMagnitude());

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    assertEquals(0, myRoot.getMagnitude());
    assertEquals(0, proxy.getMagnitude());
  }

  public void testLocation() {
    assertNull(myRoot.getLocation(getProject()));

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    assertNull(myRoot.getLocation(getProject()));
    assertNull(proxy.getLocation(getProject()));
  }

  public void testNavigatable() {
    assertNull(myRoot.getDescriptor(null));

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    assertNull(myRoot.getDescriptor(null));
    assertNull(proxy.getDescriptor(null));
  }

  public void testShouldRun() {
    assertTrue(myRoot.shouldRun());

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    assertTrue(myRoot.shouldRun());
    assertTrue(proxy.shouldRun());
  }

  public void testFilter() {
    assertEmpty(myRoot.getChildren(Filter.NO_FILTER));
    assertEmpty(myRoot.getChildren(null));

    final RTestUnitTestProxy proxy = createTestProxy("newTest");
    myRoot.addChild(proxy);

    assertEquals(1, myRoot.getChildren(Filter.NO_FILTER).size());
    assertEmpty(myRoot.getChildren(null));
  }

  public void testGetAllTests() {
    assertOneElement(myRoot.getAllTests());

    final RTestUnitTestProxy suite1 = createTestProxy("newTest");
    myRoot.addChild(suite1);
    final RTestUnitTestProxy proxy11 = createTestProxy("newTest");
    suite1.addChild(proxy11);

    final RTestUnitTestProxy suite2 = createTestProxy("newTest");
    suite1.addChild(suite2);
    final RTestUnitTestProxy proxy21 = createTestProxy("newTest");
    suite2.addChild(proxy21);
    final RTestUnitTestProxy proxy22 = createTestProxy("newTest");
    suite2.addChild(proxy22);

    assertEquals(6, myRoot.getAllTests().size());
    assertEquals(5, suite1.getAllTests().size());
    assertEquals(3, suite2.getAllTests().size());
    assertOneElement(proxy11.getAllTests());
    assertOneElement(proxy21.getAllTests());
  }
}
