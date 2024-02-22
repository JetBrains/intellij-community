// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.jetbrains.python.sdk.pythonSdk

class UnsatisfiedRequirementInspectionTest : PythonDependencyTestCase() {
  fun testUnsatisfiedRequirement() {
    doMultiFileTest("requirements.txt")
    assertContainsElements(myFixture.availableIntentions.map { it.text }, "Install package mypy", "Install all missing packages", "Run 'pip install -e .'")
  }

  fun testEmptyRequirementsFile() {
    doMultiFileTest("requirements.txt")
    assertContainsElements(myFixture.availableIntentions.map { it.text }, "Add imported packages to requirements…")
  }

  private fun doMultiFileTest(filename: String) {
    myFixture.copyDirectoryToProject(getTestName(false), "")
    myFixture.configureFromTempProjectFile(filename)
    myFixture.enableInspections(UnsatisfiedRequirementInspection::class.java)
    myFixture.checkHighlighting(true, false, true)
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