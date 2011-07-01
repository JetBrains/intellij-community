package com.jetbrains.python.refactoring;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.refactoring.move.PyMoveClassOrFunctionProcessor;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionTest extends PyLightFixtureTestCase {
  public void testFunction() {
    doTest("f", "b.py");
  }

  public void testClass() {
    doTest("C", "b.py");
  }

  // PY-3929
  public void testImportAs() {
    doTest("f", "b.py");
  }

  private void doTest(final String symbolName, final String toFileName) {
    String root = "/refactoring/moveClassOrFunction/" + getTestName(true);
    String rootBefore = root + "/before/src";
    String rootAfter = root + "/after/src";
    VirtualFile dir1 = myFixture.copyDirectoryToProject(rootBefore, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();

    PsiNamedElement element = findFirstNamedElement(symbolName);
    assertNotNull(element);

    VirtualFile toVirtualFile = dir1.findFileByRelativePath(toFileName);
    assertNotNull(toVirtualFile);
    PyFile toFile = (PyFile)PsiManager.getInstance(myFixture.getProject()).findFile(toVirtualFile);

    new PyMoveClassOrFunctionProcessor(myFixture.getProject(), new PsiNamedElement[] {element}, toFile, false).run();

    VirtualFile dir2 = getVirtualFileByName(PythonTestUtil.getTestDataPath() + rootAfter);
    try {
      PlatformTestUtil.assertDirectoriesEqual(dir2, dir1, null);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  private static VirtualFile getVirtualFileByName(String fileName) {
    return LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'));
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

