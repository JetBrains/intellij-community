package com.jetbrains.python;

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author peter
 */
public class PythonAutoPopupTest extends PyTestCase {
  private static final String FOO_CLASS = "class Foo(object):\n" +
                                          "    def bar(self):\n" +
                                          "        pass\n\n";
  private CompletionAutoPopupTester myTester;

  public void setUp() throws Exception {
    super.setUp();
    myTester = new CompletionAutoPopupTester(myFixture);
  }

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  protected void invokeTestRunnable(Runnable runnable) throws Exception {
    myTester.runWithAutoPopupEnabled(runnable);
  }

  public void testAutoPopupAfterDot() {
    myFixture.configureByText("a.py", FOO_CLASS + "Foo()<caret>");
    myTester.typeWithPauses(".");
    assertNotNull(myTester.getLookup());
  }

}
