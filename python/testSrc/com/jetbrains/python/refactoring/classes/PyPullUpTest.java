package com.jetbrains.python.refactoring.classes;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.pullUp.PyPullUpHelper;

import java.util.Collections;

/**
 * @author Dennis.Ushakov
 */
public class PyPullUpTest extends PyClassRefactoringTest {
  public void testSimple() {
    doHelperTest("Boo", ".boo", "Foo");
  }

  public void testSuperclass() {
    doHelperTest("Boo", "Foo", "Zope");
  }

  public void testExistingsuperclass() {
    doHelperTest("Boo", "Foo", "Zope");
  }

  public void testWithComments() {
    doHelperTest("Boo", ".boo", "Foo");
  }

  public void testWithMultilineComments() {
    doHelperTest("Boo", ".boo", "Foo");
  }

  public void testMultiFile() {   // PY-2810
    myFixture.copyDirectoryToProject("refactoring/pullup/multiFile/", "");
    doPullUp("AnyClass", ".this_should_be_in_super", "SuperClass");
    myFixture.checkResultByFile("SuperClass.py", "/refactoring/pullup/multiFile/SuperClass.after.py", true);
  }

  private void doHelperTest(final String className, final String memberName, final String superClassName) {
    String baseName = "/refactoring/pullup/" + getTestName(true);
    myFixture.configureByFile(baseName + ".py");
    doPullUp(className, memberName, superClassName);
    myFixture.checkResultByFile(baseName + ".after.py");
  }

  private void doPullUp(String className, String memberName, String superClassName) {
    final PyClass clazz = findClass(className);
    final PyElement member = findMember(className, memberName);
    final PyClass superClass = findClass(superClassName);
    PyPullUpHelper.pullUp(clazz, Collections.singleton(new PyMemberInfo(member)), superClass);
  }
}
