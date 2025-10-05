// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.codeInsight.stubs.PyStubPackagesAdvertiser
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.sdk.pythonSdk

@TestDataPath("\$CONTENT_ROOT/../testData/requirements/inspections")
class PyStubPackagesAdvertiserTest : PythonDependencyTestCase() {
  fun testAdvertiseImportedPackageIfRequiredStubExists() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false))
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("3.0.0")))
    initTestPackageManager(provider)


    doMultiFileTest("pandas_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertSize(1, highlightInfos)
    assertEquals(HighlightSeverity.WARNING, highlightInfos[0].severity)
    assertEquals(PyBundle.message("code.insight.type.hints.are.not.installed", "pandas-stubs"), highlightInfos[0].description)
  }

  fun testAdvertiseImportedPackageIfCompatibleStubExists() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false))
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("3.0.0.1")))
    initTestPackageManager(provider)


    doMultiFileTest("pandas_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertSize(1, highlightInfos)
    assertEquals(HighlightSeverity.WARNING, highlightInfos[0].severity)
    assertEquals(PyBundle.message("code.insight.type.hints.are.not.installed", "pandas-stubs"), highlightInfos[0].description)
  }

  fun testNotAdvertiseNotImportedPackageIfRequiredStubExists() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false))
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("3.0.0")))
    initTestPackageManager(provider)


    doMultiFileTest("numpy_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertEmpty(highlightInfos)
  }

  fun testNotAdvertiseImportedPackageIfRequiredStubDoesNotExists() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("pandas", "3.0.0", false))
      .withRepoPackagesVersions(mapOf("pandas" to listOf("3.0.0"), "pandas-stubs" to listOf("2.9.0")))
    initTestPackageManager(provider)


    doMultiFileTest("pandas_example.py")
    val highlightInfos = myFixture.doHighlighting()
    assertEmpty(highlightInfos)
  }


  private fun doMultiFileTest(filename: String) {
    myFixture.copyDirectoryToProject(this::class.java.simpleName, "")
    myFixture.configureFromTempProjectFile(filename)
    getPythonSdk(myFixture.file)!!
    myFixture.enableInspections(PyStubPackagesAdvertiser::class.java)
  }

  override fun setUp() {
    super.setUp()
    InspectionProfileImpl.INIT_INSPECTIONS = true
    myFixture.project.pythonSdk = projectDescriptor.sdk
  }

  override fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
    super.tearDown()
  }


  override fun getBasePath(): String {
    return super.getBasePath() + "inspections/"
  }
}