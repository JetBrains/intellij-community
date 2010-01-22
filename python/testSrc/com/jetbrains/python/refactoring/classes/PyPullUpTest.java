package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.refactoring.classes.pullUp.PyPullUpHelper;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Dennis.Ushakov
 */
public class PyPullUpTest extends PyClassRefactoringTest {
  public void testSimple() throws Exception {
    doHelperTest("Boo", ".boo", "Foo");
  }

  public void testSuperclass() throws Exception {
    doHelperTest("Boo", "Foo", "Zope");
  }

  public void testExistingsuperclass() throws Exception {
    doHelperTest("Boo", "Foo", "Zope");
  }

  private void doHelperTest(final String className, final String memberName, final String superClassName) throws Exception {
    String baseName = "/" + getTestName(true);
    myFixture.configureByFile(baseName + ".py");
    final PyClass clazz = findClass(className);
    final PyElement member = findMember(className, memberName);
    final PyClass superClass = findClass(superClassName);
    PyPullUpHelper.pullUp(clazz, Collections.singleton(new PyMemberInfo(member)), superClass);
    myFixture.checkResultByFile(baseName + ".after.py");
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/refactoring/pullup/";
  }
}
