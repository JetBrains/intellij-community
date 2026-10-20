// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.findPythonInterpreterForTests
import com.jetbrains.python.inspections.dependencies.DependenciesInspection
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.RequirementsProviderType
import com.jetbrains.python.packaging.management.TestPackageManagerProvider
import org.assertj.core.api.Assertions.assertThat

/**
 * Light counterpart of [com.intellij.python.junit5Tests.env.packaging.DependenciesPsiProviderTest], which needs a real
 * interpreter and therefore never runs outside the environment tests: the package manager is mocked instead, so the
 * inspection's own branches are exercised by the ordinary test suite.
 */
@TestFor(issues = ["PY-91546"], classes = [DependenciesInspection::class])
@Subsystems.PackagingRequirements
@Components.Inspection
@Layers.Functional
class DependenciesInspectionTest : PythonDependencyTestCase() {

  fun testNotInstalledRequirementIsReported() {
    val highlights = highlight(TestPackageManagerProvider(), "mypy\n")

    val warning = highlights.single()
    assertEquals(HighlightSeverity.WARNING, warning.severity)
    assertEquals(PyBundle.message("INSP.dependencies.package.not.installed", "mypy"), warning.description)
    assertContainsElements(intentionsAt("mypy"), PyBundle.message("QFIX.NAME.install.requirement", "mypy"))
  }

  // Installing every missing requirement at once only makes sense when there is more than one of them.
  fun testASingleNotInstalledRequirementIsNotOfferedTheInstallAllFix() {
    highlight(TestPackageManagerProvider(), "mypy\n")

    assertDoesntContain(intentionsAt("mypy"), PyBundle.message("QFIX.NAME.install.all.requirements"))
  }

  fun testEveryNotInstalledRequirementIsReportedAndSharesTheInstallAllFix() {
    val highlights = highlight(TestPackageManagerProvider(), "mypy\nruff\n")

    assertSameElements(
      highlights.map { it.description },
      PyBundle.message("INSP.dependencies.package.not.installed", "mypy"),
      PyBundle.message("INSP.dependencies.package.not.installed", "ruff"),
    )
    for (name in listOf("mypy", "ruff")) {
      assertContainsElements(
        intentionsAt(name),
        PyBundle.message("QFIX.NAME.install.requirement", name),
        PyBundle.message("QFIX.NAME.install.all.requirements"),
      )
    }
  }

  fun testInstalledRequirementIsNotReported() {
    val provider = TestPackageManagerProvider().withPackageInstalled(PythonPackage("mypy", "1.0", false))

    assertEmpty(highlight(provider, "mypy\n"))
  }

  fun testOutdatedRequirementIsReported() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("mypy", "1.0", false))
      .withOutdatedPackages(PythonOutdatedPackage("mypy", "1.0", "2.0"))

    val highlights = highlight(provider, "mypy\n")

    val warning = highlights.single()
    assertEquals(HighlightSeverity.WEAK_WARNING, warning.severity)
    assertEquals(PyBundle.message("python.sdk.inspection.message.version.outdated.latest", "mypy", "1.0", "2.0"),
                 warning.description)
    assertContainsElements(intentionsAt("mypy"), PyBundle.message("QFIX.NAME.update.requirement", "mypy"))
    assertDoesntContain(intentionsAt("mypy"), PyBundle.message("QFIX.NAME.update.all.requirements"))
  }

  fun testEveryOutdatedRequirementIsReportedAndSharesTheUpdateAllFix() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("mypy", "1.0", false), PythonPackage("ruff", "0.1", false))
      .withOutdatedPackages(PythonOutdatedPackage("mypy", "1.0", "2.0"), PythonOutdatedPackage("ruff", "0.1", "0.9"))

    val highlights = highlight(provider, "mypy\nruff\n")

    assertSize(2, highlights)
    for (name in listOf("mypy", "ruff")) {
      assertContainsElements(
        intentionsAt(name),
        PyBundle.message("QFIX.NAME.update.requirement", name),
        PyBundle.message("QFIX.NAME.update.all.requirements"),
      )
    }
  }

  // The outdated snapshot is refreshed asynchronously, so it can still name a version that is already installed.
  fun testARequirementAtTheLatestVersionIsNotReported() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("mypy", "2.0", false))
      .withOutdatedPackages(PythonOutdatedPackage("mypy", "1.0", "2.0"))

    assertEmpty(highlight(provider, "mypy\n"))
  }

  // The version an installed package reports wins over the one recorded in the outdated snapshot.
  fun testAnOutdatedRequirementIsReportedWithTheInstalledVersion() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("mypy", "1.5", false))
      .withOutdatedPackages(PythonOutdatedPackage("mypy", "1.0", "2.0"))

    val highlights = highlight(provider, "mypy\n")

    assertEquals(PyBundle.message("python.sdk.inspection.message.version.outdated.latest", "mypy", "1.5", "2.0"),
                 highlights.single().description)
  }

  // A reload invalidates the declared-dependencies cache. The inspection stays available.
  @TestFor(issues = ["PY-93327"])
  fun testAnOutdatedRequirementIsStillReportedAfterAPackageReload() {
    val provider = TestPackageManagerProvider()
      .withPackageInstalled(PythonPackage("mypy", "1.0", false))
      .withOutdatedPackages(PythonOutdatedPackage("mypy", "1.0", "2.0"))
    highlight(provider, "mypy\n")

    reloadPackages()

    assertThat(myFixture.doHighlighting().single().description)
      .isEqualTo(PyBundle.message("python.sdk.inspection.message.version.outdated.latest", "mypy", "1.0", "2.0"))
  }

  // The gate of the test above, asserted on its own. The installed-version hints read the same snapshot.
  @TestFor(issues = ["PY-93329"])
  fun testTheDependencyFileIsStillTrackedAfterAPackageReload() {
    highlight(TestPackageManagerProvider(), "mypy\n")
    val dependencyFile = myFixture.file

    reloadPackages()

    assertThat(packageManager().tracksDependencyFile(dependencyFile)).isTrue()
  }

  fun testEmptyFileIsReportedWithAnExportFix() {
    val highlights = highlight(TestPackageManagerProvider(), "")

    val warning = highlights.single()
    assertEquals(HighlightSeverity.WARNING, warning.severity)
    assertEquals(PyPsiBundle.message("INSP.package.requirements.requirements.file.empty"), warning.description)
    assertContainsElements(myFixture.availableIntentions.map { it.text }, PyBundle.message("QFIX.NAME.export.dependencies"))
  }

  fun testAFileOfWhitespaceIsReportedAsEmpty() {
    val highlights = highlight(TestPackageManagerProvider(), "\n  \n")

    assertEquals(PyPsiBundle.message("INSP.package.requirements.requirements.file.empty"), highlights.single().description)
  }

  // A requirement naming the project's own module is the module itself, not a package to install. The missing
  // requirement on the second line is there to show that the file is inspected at all.
  fun testARequirementNamedAfterAProjectModuleIsNotReported() {
    val highlights = highlight(TestPackageManagerProvider(), "${myFixture.module.name}\nmypy\n")

    assertEquals(PyBundle.message("INSP.dependencies.package.not.installed", "mypy"), highlights.single().description)
  }

  // Only the dependency file the interpreter is associated with is inspected; a second requirements file is not.
  fun testAnUntrackedRequirementsFileIsNotInspected() {
    val provider = TestPackageManagerProvider().withPackageInstalled(PythonPackage("mypy", "1.0", false))

    val highlights = highlight(provider, "mypy\n",
                               otherFiles = mapOf("dev-requirements.txt" to "ruff\n"),
                               configuredFile = "dev-requirements.txt")

    assertEmpty(highlights)
  }

  private fun packageManager(): PythonPackageManager =
    PythonPackageManager.forPythonInterpreter(myFixture.project, findPythonInterpreterForTests(myFixture.module)!!)

  /** The same reload a package operation and a background SDK update perform. */
  private fun reloadPackages() {
    val manager = packageManager()
    // the reload runs read actions, which a plain `runBlocking` on the EDT would deadlock against a
    // pending background write action
    runWithModalProgressBlocking(myFixture.project, "Reloading the test packages") {
      manager.reloadPackages()
    }
  }

  private fun intentionsAt(text: String): List<String> {
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf(text))
    return myFixture.availableIntentions.map { it.text }
  }

  private fun highlight(
    provider: TestPackageManagerProvider,
    requirements: String,
    otherFiles: Map<String, String> = emptyMap(),
    configuredFile: String = RequirementsProviderType.REQUIREMENTS_TXT.filename,
  ): List<HighlightInfo> {
    initTestPackageManager(provider)
    myFixture.enableInspections(DependenciesInspection::class.java)
    myFixture.addFileToProject(RequirementsProviderType.REQUIREMENTS_TXT.filename, requirements)
    otherFiles.forEach { (name, text) -> myFixture.addFileToProject(name, text) }
    setDependencyRoot(RequirementsProviderType.REQUIREMENTS_TXT)
    myFixture.configureFromTempProjectFile(configuredFile)
    return myFixture.doHighlighting()
  }

  override fun setUp() {
    super.setUp()
    InspectionProfileImpl.INIT_INSPECTIONS = true
  }

  override fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
    super.tearDown()
  }
}
