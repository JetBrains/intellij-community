// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.application.options.RegistryManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.fixtures.PyInspectionTestCase
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.NonNls

class PyRelativeImportInspectionTest: PyInspectionTestCase() {
  override fun setUp() {
    super.setUp()
    setLanguageLevel(LanguageLevel.getLatest())
    RegistryManager.getInstance()["python.explicit.namespace.packages"].resetToDefault()
  }

  override fun tearDown() {
    setLanguageLevel(LanguageLevel.getDefault())
    RegistryManager.getInstance()["python.explicit.namespace.packages"].resetToDefault()
    super.tearDown()
  }

  override fun getInspectionClass(): Class<out PyInspection> {
    return PyRelativeImportInspection::class.java
  }

  fun testPlainDirectoryDottedImportDeleteDot() {
    doRelativeImportInspectionTest("$PLAIN_DIR/dottedImport.py", PyBundle.message("QFIX.change.to.same.directory.import"))
  }

  fun testPlainDirectoryDottedImportMarkDirectory() {
    doRelativeImportInspectionTest("$PLAIN_DIR/dottedImport.py", PyBundle.message("QFIX.mark.as.namespace.package", PLAIN_DIR))
    val service = PyNamespacePackagesService.getInstance(myFixture.module)
    val plainDirVirtualFile = myFixture.findFileInTempDir(PLAIN_DIR)
    assertTrue(service.isMarked(plainDirVirtualFile))
  }

  fun testPlainDirectoryInsidePackageInsidePlainDirectoryNoInspection() {
    doRelativeImportInspectionTest("$PLAIN_DIR/ordinaryPackage/nestedPlainDirectory/dottedImport.py")
  }

  fun testNestedPlainDirectoryDottedImportMarkDirectory() {
    doRelativeImportInspectionTest("$PLAIN_DIR/nestedPlainDirectory/dottedImport.py", PyBundle.message("QFIX.mark.as.namespace.package",
                                                                                                       PLAIN_DIR))
    val service = PyNamespacePackagesService.getInstance(myFixture.module)
    val plainDirVirtualFile = myFixture.findFileInTempDir(PLAIN_DIR)
    val nestedPlainDirVirtualFile = myFixture.findFileInTempDir("$PLAIN_DIR/nestedPlainDirectory")
    assertTrue(service.isMarked(plainDirVirtualFile))
    assertFalse(service.isMarked(nestedPlainDirVirtualFile))
    assertTrue(service.isNamespacePackage(plainDirVirtualFile))
    assertTrue(service.isNamespacePackage(nestedPlainDirVirtualFile))
  }

  fun testNestedPlainDirectoryNoQuickFixChangeImportIfRelativeLevelMoreThanOne() {
    myFixture.copyDirectoryToProject(testDirectoryPath, "")
    val currentFile = myFixture.configureFromTempProjectFile("$PLAIN_DIR/nestedPlainDirectory/script.py")
    configureInspection()
    assertProjectFilesNotParsed(currentFile)
    assertSdkRootsNotParsed(currentFile)
    assertEmpty(myFixture.filterAvailableIntentions(PyBundle.message("QFIX.change.to.same.directory.import")))
    assertOneElement(myFixture.filterAvailableIntentions(PyBundle.message("QFIX.mark.as.namespace.package", PLAIN_DIR)))
  }

  fun testPlainDirectoryDottedImportRegistryOffNoInspection() {
    RegistryManager.getInstance()["python.explicit.namespace.packages"].setValue(false)
    doMultiFileTest("$PLAIN_DIR/dottedImport.py")
  }

  fun testPlainDirectoryDottedImportFromDotTwoElementsWithAs() {
    doRelativeImportInspectionTest("$PLAIN_DIR/script.py", PyBundle.message("QFIX.change.to.same.directory.import"))
  }

  fun testPlainDirectoryDottedImportFromTwoElementsWithAs() {
    doRelativeImportInspectionTest("$PLAIN_DIR/script.py", PyBundle.message("QFIX.change.to.same.directory.import"))
  }

  fun testSourceRootDottedImportInspectionWithoutQuickFixes() {
    myFixture.copyDirectoryToProject(testDirectoryPath, "")
    runWithSourceRoots(listOf(myFixture.findFileInTempDir("$PLAIN_DIR/sourceRoot"))) {
      val currentFile = myFixture.configureFromTempProjectFile("$PLAIN_DIR/sourceRoot/script.py")
      configureInspection()
      assertProjectFilesNotParsed(currentFile)
      assertSdkRootsNotParsed(currentFile)
      assertEmpty(myFixture.filterAvailableIntentions(PyBundle.message("QFIX.mark.as.namespace.package", PLAIN_DIR)))
      assertEmpty(myFixture.filterAvailableIntentions(PyBundle.message("QFIX.mark.as.namespace.package", "$PLAIN_DIR/sourceRoot")))
      assertEmpty(myFixture.filterAvailableIntentions(PyBundle.message("QFIX.change.to.same.directory.import")))
    }
  }

  fun testNamespacePackageSameDirectoryImportNoInspection() {
    doNamespacePackageTest("$NAMESPACE_PACK_DIR/mod.py", NAMESPACE_PACK_DIR)
  }

  fun testNamespacePackageDottedImportNoInspection() {
    doNamespacePackageTest("$NAMESPACE_PACK_DIR/mod.py", NAMESPACE_PACK_DIR)
  }

  fun testNamespacePackageSameDirectoryImportRegistryOffNoInspection() {
    RegistryManager.getInstance()["python.explicit.namespace.packages"].setValue(false)
    doNamespacePackageTest("$NAMESPACE_PACK_DIR/mod.py", NAMESPACE_PACK_DIR)
  }

  fun testNestedNamespacePackageSameDirectoryImportRegistryOffNoInspection() {
    RegistryManager.getInstance()["python.explicit.namespace.packages"].setValue(false)
    doNamespacePackageTest("$NAMESPACE_PACK_DIR/nestedNamespacePackage/mod.py", NAMESPACE_PACK_DIR)
  }

  fun testNotMarkedNamespacePackageInsidePackageSameDirectoryImportNoInspection() {
    doNamespacePackageTest("$NAMESPACE_PACK_DIR/$ORDINARY_PACK_DIR/nestedNamespacePackage/mod.py", NAMESPACE_PACK_DIR)
  }

  fun testOrdinaryPackageSameDirectoryImportNoInspection() {
    doRelativeImportInspectionTest("$ORDINARY_PACK_DIR/script.py")
  }

  fun testOrdinaryPackageDottedImportNoInspection() {
    doRelativeImportInspectionTest("$ORDINARY_PACK_DIR/script.py")
  }

  fun testPython2PlainDirectoryNoInspection() {
    runWithLanguageLevel(LanguageLevel.PYTHON27) {
      doRelativeImportInspectionTest("$PLAIN_DIR/script.py")
    }
  }

  private fun doRelativeImportInspectionTest(filename: String, hint: String? = null) {
    doMultiFileTest(filename)
    if (hint != null) {
      val intentionAction = myFixture.findSingleIntention(hint)
      myFixture.launchAction(intentionAction)
      myFixture.checkHighlighting(isWarning, isInfo, isWeakWarning)
      myFixture.checkResultByFile(filename, getExpectedFilePathAfterFix(filename), true)
    }
  }

  private fun doNamespacePackageTest(filename: String, directoryToMark: String, hint: String? = null) {
    myFixture.copyDirectoryToProject(testDirectoryPath, "")
    toggleNamespacePackageDirectory(directoryToMark)
    val currentFile = myFixture.configureFromTempProjectFile(filename)
    configureInspection()
    assertProjectFilesNotParsed(currentFile)
    assertSdkRootsNotParsed(currentFile)
    if (hint != null) {
      val intentionAction = myFixture.findSingleIntention(hint)
      myFixture.launchAction(intentionAction)
      myFixture.checkResultByFile(filename, getExpectedFilePathAfterFix(filename), true)
    }
  }

  private fun toggleNamespacePackageDirectory(directory: String) {
    PyNamespacePackagesService
      .getInstance(myFixture.module)
      .toggleMarkingAsNamespacePackage(myFixture.findFileInTempDir(directory))
  }

  private fun getExpectedFilePathAfterFix(originalFileName: String): @NonNls String =
    "$testDirectoryPath/${originalFileName.removeSuffix(".py")}_after.py"

  companion object {
    private const val PLAIN_DIR = "plainDirectory"
    private const val NAMESPACE_PACK_DIR = "namespacePackage"
    private const val ORDINARY_PACK_DIR = "ordinaryPackage"
  }
}