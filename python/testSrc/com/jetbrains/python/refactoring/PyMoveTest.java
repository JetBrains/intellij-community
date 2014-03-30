/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.SystemProperties;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.refactoring.move.PyMoveClassOrFunctionProcessor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;

/**
 * @author vlan
 */
public class PyMoveTest extends PyTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SystemProperties.setTestUserName("user1");
  }

  public void testFunction() {
    doMoveSymbolTest("f", "b.py");
  }

  public void testClass() {
    doMoveSymbolTest("C", "b.py");
  }

  // PY-3929
  // PY-4095
  public void testImportAs() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-3929
  public void testQualifiedImport() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-4074
  public void testNewModule() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-4098
  public void testPackageImport() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-4130
  // PY-4131
  public void testDocstringTypes() {
    doMoveSymbolTest("C", "b.py");
  }

  // PY-4182
  public void testInnerImports() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-5489
  public void testImportSlash() {
    doMoveSymbolTest("function_2", "file2.py");
  }

  // PY-5489
  public void testImportFirstWithSlash() {
    doMoveSymbolTest("function_1", "file2.py");
  }

  // PY-4545
  public void testBaseClass() {
    doMoveSymbolTest("B", "b.py");
  }

  // PY-4379
  public void testModule() {
    doMoveFileTest("p1/p2/m1.py", "p1");
  }

  // PY-5168
  public void testModuleToNonPackage() {
    doMoveFileTest("p1/p2/m1.py", "nonp3");
  }

  // PY-6432
  public void testStarImportWithUsages() {
    doMoveSymbolTest("f", "c.py");
  }

  // PY-6447
  public void testFunctionToUsage() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-5850
  public void testSubModuleUsage() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-6465
  public void testUsageFromFunction() {
    doMoveSymbolTest("use_f", "b.py");
  }

  // PY-6571
  public void testStarImportUsage() {
    doMoveSymbolTest("g", "c.py");
  }

  private void doMoveFileTest(String fileName, String toDirName)  {
    Project project = myFixture.getProject();
    PsiManager manager = PsiManager.getInstance(project);

    String root = "/refactoring/move/" + getTestName(true);
    String rootBefore = root + "/before/src";
    String rootAfter = root + "/after/src";

    VirtualFile dir1 = myFixture.copyDirectoryToProject(rootBefore, "");
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    VirtualFile virtualFile = dir1.findFileByRelativePath(fileName);
    assertNotNull(virtualFile);
    PsiElement file = manager.findFile(virtualFile);
    if (file == null) {
      file = manager.findDirectory(virtualFile);
    }
    assertNotNull(file);
    VirtualFile toVirtualDir = dir1.findFileByRelativePath(toDirName);
    assertNotNull(toVirtualDir);
    PsiDirectory toDir = manager.findDirectory(toVirtualDir);
    new MoveFilesOrDirectoriesProcessor(project, new PsiElement[] {file}, toDir, false, false, null, null).run();

    VirtualFile dir2 = getVirtualFileByName(PythonTestUtil.getTestDataPath() + rootAfter);
    try {
      PlatformTestUtil.assertDirectoriesEqual(dir2, dir1);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doMoveSymbolTest(String symbolName, String toFileName) {
    String root = "/refactoring/move/" + getTestName(true);
    String rootBefore = root + "/before/src";
    String rootAfter = root + "/after/src";
    VirtualFile dir1 = myFixture.copyDirectoryToProject(rootBefore, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();

    PsiNamedElement element = findFirstNamedElement(symbolName);
    assertNotNull(element);

    VirtualFile toVirtualFile = dir1.findFileByRelativePath(toFileName);
    String path = toVirtualFile != null ? toVirtualFile.getPath() : (dir1.getPath() + "/" + toFileName);
    new PyMoveClassOrFunctionProcessor(myFixture.getProject(),
                                       new PsiNamedElement[] {element},
                                       path,
                                       false).run();

    VirtualFile dir2 = getVirtualFileByName(PythonTestUtil.getTestDataPath() + rootAfter);
    try {
      PlatformTestUtil.assertDirectoriesEqual(dir2, dir1);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  private PsiNamedElement findFirstNamedElement(String name) {
    final Collection<PyClass> classes = PyClassNameIndex.find(name, myFixture.getProject(), false);
    if (classes.size() > 0) {
      return classes.iterator().next();
    }
    final Collection<PyFunction> functions = PyFunctionNameIndex.find(name, myFixture.getProject());
    if (functions.size() > 0) {
      return functions.iterator().next();
    }
    return null;
  }
}

