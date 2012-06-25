package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.command.WriteCommandAction;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.pushDown.PyPushDownProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownTest extends PyClassRefactoringTest {
  public void testSimple() throws Exception {
    doProcessorTest("Foo", null, ".foo");
  }

  public void testSuperclass() throws Exception {
    doProcessorTest("Zope", null, "Foo");
  }

  public void testMultiple() throws Exception {
    doProcessorTest("Foo", null, ".foo");
  }

  public void testPy346() throws Exception {
    doProcessorTest("A", null, ".meth_a1", ".meth_a2");
  }

  public void testExistingmethod() throws Exception {
    doProcessorTest("Foo", "method <b><code>foo</code></b> is already overridden in class <b><code>Boo</code></b>. Method will not be pushed down to that class.", ".foo");
  }

  private void doProcessorTest(final String className, final String expectedError, final String... membersName) throws Exception {
    try {
    String baseName = "/refactoring/pushdown/" + getTestName(true);
    myFixture.configureByFile(baseName + ".before.py");
    final PyClass clazz = findClass(className);
    final List<PyMemberInfo> members = new ArrayList<PyMemberInfo>();
    for (String memberName : membersName) {
      final PyElement member = findMember(className, memberName);
      assertNotNull(member);
      members.add(new PyMemberInfo(member));
    }

    final PyPushDownProcessor processor = new PyPushDownProcessor(myFixture.getProject(), clazz, members);
    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        processor.run();
      }
    }.execute().throwException();
    myFixture.checkResultByFile(baseName + ".after.py");
    } catch (Exception e) {
      if (expectedError == null) throw e;
      assertTrue(e.getMessage(), e.getMessage().contains(expectedError));
    }
  }
}
