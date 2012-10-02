package com.jetbrains.python;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
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

  public void testAutoPopupAfterDotDuringAnotherAutoPopup() {
    myFixture.configureByText("a.py", FOO_CLASS +
                                      "foo = Foo()\n" +
                                      "foo2 = Foo()\n" +
                                      "<caret>");
    myTester.typeWithPauses("foo");
    LookupImpl lookup1 = myTester.getLookup();
    assertNotNull(lookup1);
    assertFalse(lookup1.isFocused());

    myTester.typeWithPauses(".");
    LookupImpl lookup2 = myTester.getLookup();
    assertNotSame(lookup1, lookup2);
    assertNotNull(lookup2);
    assertFalse(lookup2.isFocused());
  }

}
