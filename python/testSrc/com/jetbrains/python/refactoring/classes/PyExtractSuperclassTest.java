package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.command.WriteCommandAction;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.extractSuperclass.PyExtractSuperclassHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyExtractSuperclassTest extends PyClassRefactoringTest {
  public void testSimple() throws Exception {
    doHelperTest("Foo", "Suppa", null, ".foo");
  }

  private void doHelperTest(final String className, final String superclassName, final String expectedError, final String... membersName) throws Exception {
    try {
    String baseName = getTestName(true);
    myFixture.configureByFile(baseName + ".before.py");
    final PyClass clazz = findClass(className);
    final List<PyMemberInfo> members = new ArrayList<PyMemberInfo>();
    for (String memberName : membersName) {
      final PyElement member = findMember(className, memberName);
      assertNotNull(member);
      members.add(new PyMemberInfo(member));
    }

    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() throws Throwable {
        //noinspection ConstantConditions
        PyExtractSuperclassHelper.extractSuperclass(clazz, members, superclassName, myFixture.getFile().getVirtualFile().getUrl());
      }
    }.execute();
    myFixture.checkResultByFile(baseName + ".after.py");
    } catch (Exception e) {
      if (expectedError == null) throw e;
      assertEquals(expectedError, e.getMessage());
    }
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/refactoring/extractsuperclass/";
  }
}
