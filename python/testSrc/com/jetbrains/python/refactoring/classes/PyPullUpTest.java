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

  private void doHelperTest(final String className, final String memberName, final String superClassName) {
    String baseName = "/refactoring/pullup/" + getTestName(true);
    myFixture.configureByFile(baseName + ".py");
    final PyClass clazz = findClass(className);
    final PyElement member = findMember(className, memberName);
    final PyClass superClass = findClass(superClassName);
    PyPullUpHelper.pullUp(clazz, Collections.singleton(new PyMemberInfo(member)), superClass);
    myFixture.checkResultByFile(baseName + ".after.py");
  }
}
