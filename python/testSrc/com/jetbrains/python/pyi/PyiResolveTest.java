/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.pyi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyMultiFileResolveTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;

public class PyiResolveTest extends PyMultiFileResolveTestCase {
  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/pyi/resolve";
  }

  public void testClassInsidePyiFile() {
    assertResolvesTo(PyClass.class, "C");
  }

  public void testBuiltinInt() {
    assertResolvesTo(PyClass.class, "int");
  }

  public void testFromPyiToClassInPy() {
    assertResolvesTo(PyClass.class, "C");
  }

  public void testModuleAttribute() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-21231
  public void testModuleAttributePyiOverPy() {
    final PsiElement result = doResolve();
    assertInstanceOf(result, PyTargetExpression.class);
    final PyTargetExpression target = (PyTargetExpression)result;
    assertEquals("foo", target.getName());
    final PsiFile containingFile = target.getContainingFile();
    assertInstanceOf(containingFile, PyiFile.class);
  }

  // PY-21231
  public void testGenericAttribute() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testForwardReference() {
    assertResolvesTo(PyClass.class, "C");
  }

  public void testCanonicalName() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    PyClass exportedClass = PyClassNameIndex.findClass("pkg.mod.Exported", myFixture.getProject());
    QualifiedName exportedCanonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(exportedClass, null);
    assertEquals(QualifiedName.fromDottedString("pkg"), exportedCanonicalImportPath);

    PyClass internalClass = PyClassNameIndex.findClass("pkg.mod.Internal", myFixture.getProject());
    QualifiedName internalCanonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(internalClass, null);
    assertEquals(QualifiedName.fromDottedString("pkg.mod"), internalCanonicalImportPath);
  }
}
