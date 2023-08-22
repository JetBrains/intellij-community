// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;

import java.util.Collection;


public class PyInheritorsSearchTest extends PyTestCase {
  public void testSimple() {
    setupProject();
    final PyClass pyClass = findClass("A");
    Collection<PyClass> inheritors = PyClassInheritorsSearch.search(pyClass, false).findAll();
    assertEquals(2, inheritors.size());
  }

  public void testDeep() {
    setupProject();
    final PyClass pyClass = findClass("A");
    Collection<PyClass> inheritors = PyClassInheritorsSearch.search(pyClass, true).findAll();
    assertEquals(2, inheritors.size());
  }

  public void testDotted() {
    setupProject();
    final PyClass pyClass = findClass("A");
    Collection<PyClass> inheritors = PyClassInheritorsSearch.search(pyClass, true).findAll();
    assertEquals(1, inheritors.size());
  }

  // PY-19461
  public void testInheritorsWhenSuperClassImportedWithAs() {
    setupProject();
    final PyClass pyClass = findClass("C");
    final Collection<PyClass> inheritors = PyClassInheritorsSearch.search(pyClass, false).findAll();
    assertSameElements(inheritors, findClass("D"));
  }

  private void setupProject() {
    String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
  }

  private PyClass findClass(final String name) {
    final Project project = myFixture.getProject();
    final Collection<PyClass> classes = PyClassNameIndex.find(name, project, false);
    assertEquals(1, classes.size());
    return classes.iterator().next();
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inheritors/";
  }
}
