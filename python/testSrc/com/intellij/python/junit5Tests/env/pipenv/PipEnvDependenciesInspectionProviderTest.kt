// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pipenv

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.dependencies.DependenciesInspection
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.RequirementsProviderType
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import com.jetbrains.python.requirements.PythonDependencyTestCase
import com.jetbrains.python.sdk.pythonSdk

@TestDataPath($$"$CONTENT_ROOT/../testData/pipenv/pipfile/inspections")
internal class PipEnvDependenciesInspectionProviderTest : PythonDependencyTestCase() {
  fun testPipEnvDependenciesInspectionProvider() {
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
    setDependencyRoot(RequirementsProviderType.PIPFILE)
    myFixture.configureFromTempProjectFile("Pipfile")

    myFixture.enableInspections(DependenciesInspection::class.java)
    myFixture.checkHighlighting(true, false, true, false)

    for (pkg in listOf("django", "flask")) {
      val offset = myFixture.editor.document.text.indexOf("$pkg =")
      myFixture.editor.caretModel.moveToOffset(offset)

      val availableIntentions = myFixture.availableIntentions.map { it.text }
      val updateFixText = PyBundle.message("QFIX.NAME.update.requirement", pkg)

      assertContainsElements(availableIntentions, updateFixText)
      assertContainsElements(availableIntentions, PyBundle.message("QFIX.NAME.update.all.requirements"))
    }

    for (pkg in listOf("numpy")) {
      val offset = myFixture.editor.document.text.indexOf("$pkg =")
      myFixture.editor.caretModel.moveToOffset(offset)

      val availableIntentions = myFixture.availableIntentions.map { it.text }
      val installFixText = PyBundle.message("QFIX.NAME.install.requirement", pkg)

      assertContainsElements(availableIntentions, installFixText)
      assertContainsElements(availableIntentions, PyBundle.message("QFIX.NAME.install.all.requirements"))
    }
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

  override fun getBasePath(): String = "/community/python/testData/pipenv/pipfile/inspections/"
}
