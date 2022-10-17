// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.python.fixtures.PyMultiFileResolveTestCase;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.junit.Assert.assertNotEquals;


public class PyRelativeImportResolveTest extends PyMultiFileResolveTestCase {
  private static final String PLAIN_DIR = "plainDirectory";
  private static final String NAMESPACE_PACK_DIR = "namespacePackage";
  private static final String ORDINARY_PACK_DIR = "ordinaryPackage";

  private String myNamespacePackageDirectory = null;

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/multiFile/relativeAndSameDirectoryImports/";
  }

  @Override
  protected void prepareTestDirectory() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    if (myNamespacePackageDirectory != null) {
      toggleNamespacePackageDirectory(myNamespacePackageDirectory);
    }
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myNamespacePackageDirectory = null;
    RegistryManager.getInstance().get("python.explicit.namespace.packages").resetToDefault();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myNamespacePackageDirectory = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      RegistryManager.getInstance().get("python.explicit.namespace.packages").resetToDefault();
      super.tearDown();
    }
  }

  public void testOrdinaryPackageDottedRelativeFromImport() {
    doTestOrdinaryPackageFromImportOfFunction();
  }

  public void testOrdinaryPackageSameDirectoryFromImport() {
    doTestOrdinaryPackageFromImportOfFunction();
  }

  private void doTestOrdinaryPackageFromImportOfFunction() {
    myTestFileName = ORDINARY_PACK_DIR + "/mod.py";
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testNamespacePackageDottedRelativeImport() {
    doTestNamespacePackageImportOfModule();
  }

  public void testNamespacePackageSameDirectoryImport() {
    doTestNamespacePackageImportOfModule();
  }

  private void doTestNamespacePackageImportOfModule() {
    myTestFileName = NAMESPACE_PACK_DIR + "/mod.py";
    assertResolvesTo(PyFile.class, "util.py");
  }

  public void testNestedNamespacePackageDottedRelativeImport() {
    doTestNestedNamespacePackageImportOfModule();
  }

  public void testNestedNamespacePackageSameDirectoryImport() {
    doTestNestedNamespacePackageImportOfModule();
  }

  private void doTestNestedNamespacePackageImportOfModule() {
    myTestFileName = NAMESPACE_PACK_DIR + "/nestedNamespacePackage/mod.py";
    myNamespacePackageDirectory = NAMESPACE_PACK_DIR;
    assertResolvesTo(PyFile.class, "util.py");
  }

  public void testPlainDirectoryDottedRelativeImport() {
    doTestPlainDirectoryImportOfModule();
  }

  public void testPlainDirectorySameDirectoryImport() {
    doTestPlainDirectoryImportOfModule();
  }

  private void doTestPlainDirectoryImportOfModule() {
    myTestFileName = PLAIN_DIR + "/mod.py";
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testPlainDirectoryDottedRelativeImportRegistryOff() {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").setValue(false);
    doTestPlainDirectoryImportOfModule();
  }

  public void testPlainDirectorySameDirectoryImportRegistryOff() {
    RegistryManager.getInstance().get("python.explicit.namespace.packages").setValue(false);
    myTestFileName = PLAIN_DIR + "/mod.py";
    assertUnresolved();
  }

  // PY-45115
  public void testSameDirectoryImportsNotCached() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    PsiManager psiManager = myFixture.getPsiManager();

    PsiFile sameDirResolveOrigin = psiManager.findFile(myFixture.findFileInTempDir("dir/main.py"));
    PyQualifiedNameResolveContext sameDirContext = PyResolveImportUtil.fromFoothold(sameDirResolveOrigin).copyWithRelative(0);
    List<PsiElement> sameDirResults = PyResolveImportUtil.resolveQualifiedName(QualifiedName.fromDottedString("os"), sameDirContext);
    PsiElement sameDirOnlyResult = assertOneElement(sameDirResults);
    PsiFileSystemItem sameDirOsModule = assertInstanceOf(sameDirOnlyResult, PsiFileSystemItem.class);
    assertEquals(myFixture.findFileInTempDir("dir/os.py"), sameDirOsModule.getVirtualFile());
    assertTrue(psiManager.isInProject(sameDirOsModule));

    PsiFile absResolveOrigin = psiManager.findFile(myFixture.findFileInTempDir("main.py"));
    PyQualifiedNameResolveContext absContext = PyResolveImportUtil.fromFoothold(absResolveOrigin).copyWithRelative(0);
    List<PsiElement> absResults = PyResolveImportUtil.resolveQualifiedName(QualifiedName.fromDottedString("os"), absContext);
    PsiElement absOnlyResult = assertOneElement(absResults);
    PsiFileSystemItem stdlibOsModule = assertInstanceOf(absOnlyResult, PsiFileSystemItem.class);
    assertNotEquals(myFixture.findFileInTempDir("dir/os.py"), stdlibOsModule.getVirtualFile());
    assertFalse(psiManager.isInProject(stdlibOsModule));
  }

  // PY-45114
  public void testPlainDirectoryImportPrioritizeFileItselfOverSdk() {
    myTestFileName = PLAIN_DIR + "/os.py";
    assertResolvesInsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testPlainDirectoryImportPrioritizeSameDirectoryModuleOverSdk() {
    myTestFileName = PLAIN_DIR + "/script.py";
    assertResolvesInsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testPlainDirectoryInsideOrdinaryPackageImportPrioritizeSameDirectoryModuleOverSdk() {
    myTestFileName = ORDINARY_PACK_DIR + "/" + PLAIN_DIR + "/script.py";
    assertResolvesInsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testOrdinaryPackageImportPrioritizeSdkOverSameDirectoryModule() {
    myTestFileName = ORDINARY_PACK_DIR + "/script.py";
    assertResolvesOutsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testPython2OrdinaryPackageImportPrioritizeSameDirectoryModuleOverSdk() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      myTestFileName = ORDINARY_PACK_DIR + "/script.py";
      assertResolvesInsideProjectTo(PyFile.class);
    });
  }

  // PY-45114
  public void testOrdinaryPackageSourceRootImportPrioritizeSameDirectoryModuleOverSdk() {
    myTestFileName = ORDINARY_PACK_DIR + "/script.py";
    PsiFile currentFile = prepareFile();
    runWithSourceRoots(List.of(myFixture.findFileInTempDir("ordinaryPackage")), () -> {
      PsiManager psiManager = myFixture.getPsiManager();
      PsiElement element = doResolve(currentFile);
      assertInstanceOf(element, PyFile.class);
      assertTrue(psiManager.isInProject(element));
    });
  }

  // PY-45114
  public void testOrdinaryPackageRelativeFromImportPrioritizeSameDirectoryModuleOverSdk() {
    myTestFileName = ORDINARY_PACK_DIR + "/script.py";
    assertResolvesInsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testOrdinaryPackageRelativeImportSourcePrioritizeSameDirectoryModuleOverSdk() {
    myTestFileName = ORDINARY_PACK_DIR + "/script.py";
    assertResolvesInsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testOrdinaryPackageImportPrioritizeSdkOverFileItself() {
    myTestFileName = ORDINARY_PACK_DIR + "/os.py";
    assertResolvesOutsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testInitPyImportPrioritizeSubmoduleOverPackageItself() {
    myTestFileName = ORDINARY_PACK_DIR + "/__init__.py";
    assertResolvesTo(PyFile.class, "ordinaryPackage.py", "/src/" + ORDINARY_PACK_DIR + "/ordinaryPackage.py");
  }

  // PY-45114
  public void testInitPyFromImportPrioritizeNestedOrdinaryPackageOverNestedOrdinaryPackageModule() {
    myTestFileName = ORDINARY_PACK_DIR + "/__init__.py";
    assertResolvesTo(PyFile.class, "__init__.py", "/src/" + ORDINARY_PACK_DIR + "/" + ORDINARY_PACK_DIR + "/__init__.py");
  }

  // PY-45114
  public void testInitPyFromImportPrioritizeNestedOrdinaryPackageOverModule() {
    myTestFileName = ORDINARY_PACK_DIR + "/__init__.py";
    assertResolvesTo(PyFile.class, "__init__.py", "/src/" + ORDINARY_PACK_DIR + "/" + ORDINARY_PACK_DIR + "/__init__.py");
  }

  // PY-45114
  public void testInitPyImportPrioritizePackageOverSameDirectoryModule() {
    myTestFileName = ORDINARY_PACK_DIR + "/__init__.py";
    assertResolvesTo(PyFile.class, "__init__.py", "/src/" + ORDINARY_PACK_DIR + "/__init__.py");
  }

  // PY-45114
  public void testNamespacePackageImportPrioritizeSdkOverSameDirectoryModule() {
    myTestFileName = NAMESPACE_PACK_DIR + "/script.py";
    myNamespacePackageDirectory = NAMESPACE_PACK_DIR;
    assertResolvesOutsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testNamespacePackageImportPrioritizeSdkOverFileItself() {
    myTestFileName = NAMESPACE_PACK_DIR + "/os.py";
    myNamespacePackageDirectory = NAMESPACE_PACK_DIR;
    assertResolvesOutsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testPlainDirectoryImportPrioritizeUserDirectoryOverSdk() {
    myTestFileName = PLAIN_DIR + "/script.py";
    assertResolvesInsideProjectTo(PsiDirectory.class);
  }

  // PY-45114
  public void testPlainDirectoryImportPrioritizeNestedOrdinaryPackageOverSdk() {
    myTestFileName = PLAIN_DIR + "/script.py";
    assertResolvesInsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testPlainDirectoryImportPrioritizeNestedNamespacePackageOverSdk() {
    myTestFileName = PLAIN_DIR + "/script.py";
    myNamespacePackageDirectory = PLAIN_DIR + "/os";
    assertResolvesInsideProjectTo(PsiDirectory.class);
  }

  // PY-45114
  public void testOrdinaryPackageImportPrioritizeSdkOverNestedOrdinaryPackage() {
    myTestFileName = ORDINARY_PACK_DIR + "/script.py";
    assertResolvesOutsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testNamespacePackageImportPrioritizeSdkOverNestedOrdinaryPackage() {
    myTestFileName = NAMESPACE_PACK_DIR + "/script.py";
    myNamespacePackageDirectory = NAMESPACE_PACK_DIR;
    assertResolvesOutsideProjectTo(PyFile.class);
  }

  // PY-45114
  public void testPlainDirectoryOutsideProjectImportPrioritizeSdkOverSameDirectoryModule() {
    final String testDir = getTestName(true);
    runWithAdditionalClassEntryInSdkRoots(testDir + "/site-packages", () -> {
      assertResolvesTo(PyFile.class, "__init__.pyi", "__init__.pyi");
    });
  }

  public void testInitPyImportResolveSameDirectoryModule() {
    myTestFileName = ORDINARY_PACK_DIR + "/__init__.py";
    assertResolvesTo(PyFile.class, "mymod.py", "/src/" + ORDINARY_PACK_DIR + "/mymod.py");
  }

  public void testOrdinaryPackageOutsideProjectNotResolveSameDirectoryImportedModule() {
    final String testDir = getTestName(true);
    runWithAdditionalClassEntryInSdkRoots(testDir + "/site-packages", () -> {
      assertUnresolved();
    });
  }

  public void testOrdinaryPackageOutsideProjectResolveRelativeImportedModule() {
    final String testDir = getTestName(true);
    runWithAdditionalClassEntryInSdkRoots(testDir + "/site-packages", () -> {
      assertResolvesTo(PyFile.class, "mod.py", "mod.py");
    });
  }

  public void testNamespacePackageOutsideProjectNotResolveSameDirectoryImportedModule() {
    final String testDir = getTestName(true);
    runWithAdditionalClassEntryInSdkRoots(testDir + "/site-packages", () -> {
      assertUnresolved();
    });
  }

  public void testNamespacePackageOutsideProjectResolveRelativeImportedModule() {
    final String testDir = getTestName(true);
    runWithAdditionalClassEntryInSdkRoots(testDir + "/site-packages", () -> {
      assertResolvesTo(PyFile.class, "mod.py", "mod.py");
    });
  }

  // PY-45776
  public void testPlainDirectoryImportResolveSameDirectoryModuleNotThrowsException() {
    myTestFileName = "not-valid-identifier/script.py";
    assertResolvesInsideProjectTo(PyFile.class);
  }

  // PY-45776
  public void testPlainDirectoryImportResolveExcludedDirectoryModuleNotThrowsException() {
    myTestFileName = PLAIN_DIR + "/script.py";
    PsiFile currentFile = prepareFile();
    PsiTestUtil.addExcludedRoot(myFixture.getModule(), myFixture.findFileInTempDir("excluded"));
    PsiElement element = doResolve(currentFile);
    assertInstanceOf(element, PyFile.class);
    assertEquals(myFixture.findFileInTempDir(PLAIN_DIR + "/excluded.py"), ((PyFile)element).getVirtualFile());
  }

  // PY-45776
  public void testOrdinaryPackageInvalidNameImportPrioritizeModuleInRootOverSameDirectoryModule() {
    myTestFileName = "ordinary package/main.py";
    PsiFile file = assertResolvesInsideProjectTo(PyFile.class);
    assertEquals(myFixture.findFileInTempDir("mod.py"), file.getVirtualFile());
  }

  // PY-45776
  public void testPlainDirectoryInvalidNameImportPrioritizeSameDirectoryModuleOverModuleInRoot() {
    myTestFileName = "plain directory/main.py";
    PsiFile file = assertResolvesInsideProjectTo(PyFile.class);
    assertEquals(myFixture.findFileInTempDir("plain directory/mod.py"), file.getVirtualFile());
  }

  // PY-45776
  public void testOrdinaryPackageImportPrioritizeExcludedDirectoryInRootOverSameDirectoryModule() {
    myTestFileName = ORDINARY_PACK_DIR + "/script.py";
    PsiFile currentFile = prepareFile();
    PsiTestUtil.addExcludedRoot(myFixture.getModule(), myFixture.findFileInTempDir("excluded"));
    PsiElement element = doResolve(currentFile);
    assertInstanceOf(element, PsiDirectory.class);
    assertEquals(myFixture.findFileInTempDir("excluded"), ((PsiDirectory)element).getVirtualFile());
  }

  // PY-45776
  public void testOrdinaryPackageImportPrioritizeModuleInRootOverSameDirectoryExcludedDirectory() {
    myTestFileName = ORDINARY_PACK_DIR + "/script.py";
    PsiFile currentFile = prepareFile();
    PsiTestUtil.addExcludedRoot(myFixture.getModule(), myFixture.findFileInTempDir(ORDINARY_PACK_DIR + "/excluded"));
    PsiElement element = doResolve(currentFile);
    assertInstanceOf(element, PyFile.class);
    assertEquals(myFixture.findFileInTempDir("excluded.py"), ((PyFile)element).getVirtualFile());
  }

  private <T extends PsiElement> T assertResolvesInsideProjectTo(@NotNull Class<T> cls) {
    PsiManager psiManager = myFixture.getPsiManager();
    T resolved = assertResolvesToElementOfClass(cls);
    assertTrue(psiManager.isInProject(resolved));
    return resolved;
  }

  private <T extends PsiElement> T assertResolvesOutsideProjectTo(@NotNull Class<T> cls) {
    PsiManager psiManager = myFixture.getPsiManager();
    T resolved = assertResolvesToElementOfClass(cls);
    assertFalse(psiManager.isInProject(resolved));
    return resolved;
  }

  private <T extends PsiElement> @NotNull T assertResolvesToElementOfClass(@NotNull Class<T> cls) {
    PsiElement resolved = doResolve();
    return assertInstanceOf(resolved, cls);
  }

  private void toggleNamespacePackageDirectory(@NotNull String directory) {
    PyNamespacePackagesService
      .getInstance(myFixture.getModule())
      .toggleMarkingAsNamespacePackage(myFixture.findFileInTempDir(directory));
  }
}
