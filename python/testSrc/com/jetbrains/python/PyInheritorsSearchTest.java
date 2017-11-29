/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;

import java.util.Collection;

/**
 * @author yole
 */
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
