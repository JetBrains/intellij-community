// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.conda

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.dependencies.DependenciesInspection
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.requirements.PythonDependencyTestCase
import com.jetbrains.python.sdk.pythonSdk

@TestDataPath($$"$CONTENT_ROOT/../testData/conda/environmentYml/inspections")
class CondaDependenciesInspectionProviderTest : PythonDependencyTestCase() {

  fun testCondaDependenciesInspectionProvider() {
    val provider =
      TestPackageManagerProvider()
        .withPackageInstalled(
          PythonPackage("python", "3.9", false),
          PythonPackage("django", "1.3.1", false),
          PythonPackage("flask", "1.0", false),
          PythonPackage("requests", "1.22.0", false)
        )
        .withOutdatedPackages(
          PythonOutdatedPackage("django", "1.3.1", "5.0.0"),
          PythonOutdatedPackage("flask", "1.0", "3.0.0"),
        )
    initTestPackageManager(provider)

    myFixture.copyDirectoryToProject(getTestName(false), "")
    myFixture.configureFromTempProjectFile("environment.yml")

    myFixture.enableInspections(DependenciesInspection::class.java)
    myFixture.checkHighlighting(true, false, true, false)

    val djangoOffset = myFixture.editor.document.text.indexOf("django=1.3.1")
    myFixture.editor.caretModel.moveToOffset(djangoOffset)
    val updateFixName = PyBundle.message("QFIX.NAME.update.requirement", "django")
    assertContainsElements(myFixture.availableIntentions.map { it.text }, updateFixName)
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

  override fun getBasePath(): String = "/community/python/testData/conda/environmentYml/inspections/"
}
