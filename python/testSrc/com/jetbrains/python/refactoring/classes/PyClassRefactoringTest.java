package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public abstract class PyClassRefactoringTest extends PyTestCase {
  protected PyElement findMember(String className, String memberName) {
    if (!memberName.contains(".")) return findClass(memberName);
    return findMethod(className, memberName.substring(1));
  }

  private PyFunction findMethod(final String className, final String name) {
    final PyClass clazz = findClass(className);
    final PyFunction method = clazz.findMethodByName(name, false);
    assertNotNull(method);
    return method;
  }

  protected PyClass findClass(final String name) {
    final Project project = myFixture.getProject();
    final Collection<PyClass> classes = PyClassNameIndex.find(name, project, false);
    assertEquals(1, classes.size());
    return classes.iterator().next();
  }
}
