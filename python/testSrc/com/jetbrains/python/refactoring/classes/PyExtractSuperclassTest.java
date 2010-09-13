package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.command.WriteCommandAction;
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
    doHelperTest("Foo", "Suppa", null, true, ".foo");
  }

  public void testWithSuper() throws Exception {
    doHelperTest("Foo", "Suppa", null, true, ".foo");
  }

  public void testWithImport() throws Exception {
    doHelperTest("A", "Suppa", null, false, ".foo");
  }

  private void doHelperTest(final String className, final String superclassName, final String expectedError, final boolean sameFile, final String... membersName) throws Exception {
    try {
    String baseName = "/refactoring/extractsuperclass/" + getTestName(true);
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
        final String url = sameFile ? myFixture.getFile().getVirtualFile().getUrl() :
                                      myFixture.getFile().getVirtualFile().getParent().getUrl();
        PyExtractSuperclassHelper.extractSuperclass(clazz, members, superclassName, url);
      }
    }.execute();
    myFixture.checkResultByFile(baseName + ".after.py");
    } catch (Exception e) {
      if (expectedError == null) throw e;
      assertEquals(expectedError, e.getMessage());
    }
  }
}
