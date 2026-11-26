// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.stubs.PyStubPackagesCompatibilityInspection
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.TestPackageManagerProvider

@TestDataPath("\$CONTENT_ROOT/../testData/requirements/inspections")
@Subsystems.PackagingRequirements
@Components.Inspection
@Layers.Functional
class PyStubPackagesCompatibilityInspectionTest : PythonDependencyTestCase() {
  fun testAdvertiseImportedPackageIfOldStubAndCompatibleExists() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false),
                            PythonPackage("pandas-stubs", "2.9.0", false)
      )
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("3.0.0", "2.9.0")))
    initTestPackageManager(provider)


    doMultiFileTest("pandas_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertSize(1, highlightInfos)
    assertEquals(HighlightSeverity.WARNING, highlightInfos[0].severity)
    assertEquals(PyPsiBundle.message("INSP.stub.packages.compatibility.incompatible.packages.message", "pandas-stubs"), highlightInfos[0].description)
  }

  fun testAdvertiseImportedPackageIfNewStubAndCompatibleExists() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "2.9.0", false),
                            PythonPackage("pandas-stubs", "3.0.0", false)
      )
      .withRepoPackagesVersions(mapOf("pandas" to listOf("2.9.0", "3.0.0"), "pandas-stubs" to listOf("3.0.0", "2.9.0")))
    initTestPackageManager(provider)


    doMultiFileTest("pandas_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertSize(1, highlightInfos)
    assertEquals(HighlightSeverity.WARNING, highlightInfos[0].severity)
    assertEquals(PyPsiBundle.message("INSP.stub.packages.compatibility.incompatible.packages.message", "pandas-stubs"), highlightInfos[0].description)
  }


  fun testNotAdvertiseImportedPackageIfNotCompatibleAndCompatibleDoesNotExists() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false),
                            PythonPackage("pandas-stubs", "2.9.0", false)
      )
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("2.9.0")))
    initTestPackageManager(provider)


    doMultiFileTest("pandas_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertEmpty(highlightInfos)
  }

  fun testNotAdvertiseNotImportedPackageIfNotCompatibleAndCompatibleDoesNotExists() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false),
                            PythonPackage("pandas-stubs", "2.9.0", false)
      )
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("2.9.0", "3.0.0")))
    initTestPackageManager(provider)


    doMultiFileTest("numpy_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertEmpty(highlightInfos)
  }

  fun testNotAdvertiseImportedPackageIfCompatible() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false),
                            PythonPackage("pandas-stubs", "3.0.0", false)
      )
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("3.0.0.1", "3.0.0")))
    initTestPackageManager(provider)


    doMultiFileTest("numpy_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertEmpty(highlightInfos)
  }


  @TestFor(issues = ["PY-94463"])
  fun testIgnoreFixAddsStubPackageToOptions() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false),
                            PythonPackage("pandas-stubs", "2.9.0", false)
      )
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("3.0.0", "2.9.0")))
    initTestPackageManager(provider)

    doMultiFileTest("pandas_example.py")
    val inspection = PyStubPackagesCompatibilityInspection()
    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    (profile.getUnwrappedTool(inspection.shortName, myFixture.file) as PyStubPackagesCompatibilityInspection).ignoredStubPackages.add("numpy-stubs")
    myFixture.launchAction(myFixture.findSingleIntention(PyPsiBundle.message("INSP.stub.packages.compatibility.ignore")))

    val tool = profile.getUnwrappedTool(inspection.shortName, myFixture.file) as PyStubPackagesCompatibilityInspection
    assertSize(2, tool.ignoredStubPackages)
    assertEquals("numpy-stubs", tool.ignoredStubPackages[0])
    assertTrue(tool.ignoredStubPackages[1], tool.ignoredStubPackages[1].startsWith("pandas-stubs"))
    assertEmpty(myFixture.doHighlighting())
  }

  private fun doMultiFileTest(filename: String) {
    myFixture.copyDirectoryToProject(this::class.java.simpleName, "")
    myFixture.configureFromTempProjectFile(filename)
    getPythonInterpreter(myFixture.file)!!
    myFixture.enableInspections(PyStubPackagesCompatibilityInspection::class.java)
  }

  override fun setUp() {
    super.setUp()
    InspectionProfileImpl.INIT_INSPECTIONS = true
  }

  override fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
    super.tearDown()
  }


  override fun getBasePath(): String {
    return super.getBasePath() + "inspections/"
  }
}