package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.command.WriteCommandAction;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.pushDown.PyPushDownProcessor;

import java.util.Collections;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownTest extends PyClassRefactoringTest {
  public void testSimple() throws Exception {
    doProcessorTest("Foo", ".foo", null);
  }

  public void testSuperclass() throws Exception {
    doProcessorTest("Zope", "Foo", null);
  }

  public void testMultiple() throws Exception {
    doProcessorTest("Foo", ".foo", null);
  }

  public void testExistingmethod() throws Exception {
    doProcessorTest("Foo", ".foo", "function <b><code>foo</code></b> is already overridden in class <b><code>Boo</code></b>. Method will not be pushed down to that class.");
  }

  private void doProcessorTest(final String className, final String memberName, final String expectedError) throws Exception {
    try {
    String baseName = "/refactoring/pushdown/" + getTestName(true);
    myFixture.configureByFile(baseName + ".before.py");
    final PyClass clazz = findClass(className);
    final PyElement member = findMember(className, memberName);
    final PyPushDownProcessor processor = new PyPushDownProcessor(myFixture.getProject(), clazz, Collections.singleton(new PyMemberInfo(member)));
    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        processor.run();
      }
    }.execute();
    myFixture.checkResultByFile(baseName + ".after.py");
    } catch (Exception e) {
      if (expectedError == null) throw e;
      assertEquals(expectedError, e.getMessage());
    }
  }
}
