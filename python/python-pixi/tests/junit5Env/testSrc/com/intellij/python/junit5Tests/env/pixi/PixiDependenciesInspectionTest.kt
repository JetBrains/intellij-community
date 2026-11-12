// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PyBundle
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.inspections.dependencies.DependenciesInspection
import com.jetbrains.python.junit5.framework.pyCodeInsightFixture
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
@ExtendWith(PythonFactoryExtension::class, LeakedProcessReporterExtension::class)
internal class PixiDependenciesInspectionTest {
  private val rootFixture = tempPathFixture()
  private val projectFixture = projectFixture(rootFixture, openAfterCreation = true)
  private val moduleFixture = projectFixture.pyModuleFixture(rootFixture, addPathToSourceRoot = true)
  private val pyProjectFixture = moduleFixture.pyProjectFixture()
  private val codeInsightFixture = pyCodeInsightFixture(projectFixture, moduleFixture, rootFixture)

  @ParameterizedTest
  @ValueSource(strings = ["environment.yml", "environment.yaml"])
  fun `empty environment files offer export without becoming the Pixi source`(
    fileName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val root = rootFixture.get()
    val manifest = root.resolve("pixi.toml")
    manifest.writeText("""
      [workspace]
      name = 'export-inspection-test'
      channels = ['conda-forge']
      platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
      [dependencies]
      python = '3.13.*'
      [pypi-dependencies]
      six = '==1.16.0'
    """.trimIndent() + "\n")
    ExecService().execGetStdout(environment.pixiExecutable, Args("install", "--manifest-path", manifest.pathString)).getOrThrow()
    val exportFile = root.resolve(fileName)
    exportFile.writeText("")
    val manifestFile = withContext(Dispatchers.IO) {
      checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(manifest))
    }
    val virtualFile = withContext(Dispatchers.IO) {
      checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(exportFile))
    }
    val tool = PixiPyTool.getInstance()
    val previous = tool.getCustomExecutablePath(localEel.descriptor)
    tool.setCustomExecutablePath(localEel.descriptor, environment.pixiExecutable)
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    val interpreter = registry.newPythonInterpreter(pyProject, PyInterpreterRef.native(tool.fusId, PyEnvRef("default"))).getOrThrow()
    try {
      pyProject.setPythonInterpreter(interpreter).getOrThrow()
      val manager = PythonPackageManager.forPythonInterpreter(projectFixture.get(), interpreter)
      manager.listInstalledPackages()
      manager.listDeclaredPackagesCached()
      val declared = checkNotNull(manager.listDeclaredPackages()).getOrThrow().map { it.name }
      val beforeManifest = manifest.readBytes()
      val lock = root.resolve("pixi.lock")
      val beforeLock = lock.readBytes()
      val fixture = codeInsightFixture.get()
      withContext(Dispatchers.EDT) {
        fixture.configureFromExistingVirtualFile(virtualFile)
        fixture.enableInspections(DependenciesInspection())
        fixture.doHighlighting()
      }
      waitUntilAssertSucceeds("The empty environment file must offer the export quick fix.") {
        manager.listDeclaredPackagesCached()
        assertThat(manager.getRootDependenciesFile()?.virtualFile).isEqualTo(manifestFile)
        withContext(Dispatchers.EDT) {
          assertThat(DependenciesInspection().isAvailableForFile(fixture.file)).isTrue()
          fixture.doHighlighting()
          assertThat(fixture.getAllQuickFixes().map { it.text }).contains(PyBundle.message("QFIX.NAME.export.dependencies"))
        }
      }
      withContext(Dispatchers.EDT) {
        fixture.launchAction(fixture.findSingleIntention(PyBundle.message("QFIX.NAME.export.dependencies")))
      }
      waitUntilAssertSucceeds("The export quick fix must write native and Python packages.") {
        assertThat(exportFile.readText()).contains("name: default", "python ==3.13.", "six==1.16.0")
      }
      edtWriteAction { VfsUtil.saveText(virtualFile, "dependencies:\n  - pixi-export-must-not-control-sync ==999999\n") }

      manager.syncLocked().getOrThrow()

      assertThat(checkNotNull(manager.listDeclaredPackages()).getOrThrow().map { it.name }).isEqualTo(declared)
      assertThat(manager.getRootDependenciesFile()?.virtualFile?.toNioPath()).isEqualTo(manifest)
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
    finally {
      edtWriteAction { FileEditorManagerEx.getInstanceEx(projectFixture.get()).closeAllFiles() }
      pyProject.setPythonInterpreter(null).getOrThrow()
      registry.removePythonInterpreter(pyProject, interpreter)
      tool.setCustomExecutablePath(localEel.descriptor, previous)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `dependency warnings follow the selected environment features`(
    noDefaultFeature: Boolean, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val root = rootFixture.get()
    val manifest = root.resolve("pyproject.toml")
    manifest.writeText("""
      [project]
      name = 'inspection-test'
      version = '0.1.0'
      dependencies = []
      [tool.pixi.workspace]
      channels = ['conda-forge']
      platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
      [tool.pixi.feature.docs.dependencies]
      python = '3.13.*'
      [tool.pixi.environments]
      docs = { features = ['docs'], no-default-feature = $noDefaultFeature }
    """.trimIndent() + "\n")
    ExecService().execGetStdout(environment.pixiExecutable, Args(
      "install", "--manifest-path", manifest.pathString, "--environment", "docs",
    )).getOrThrow()
    val dependencyFile = withContext(Dispatchers.IO) {
      checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(manifest))
    }
    val tool = PixiPyTool.getInstance()
    val previous = tool.getCustomExecutablePath(localEel.descriptor)
    tool.setCustomExecutablePath(localEel.descriptor, environment.pixiExecutable)
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    val interpreter = registry.newPythonInterpreter(pyProject, PyInterpreterRef.native(tool.fusId, PyEnvRef("docs"))).getOrThrow()
    try {
      pyProject.setPythonInterpreter(interpreter).getOrThrow()
      val manager = PythonPackageManager.forPythonInterpreter(projectFixture.get(), interpreter)
      manager.listInstalledPackages()
      val activePlatform = if (SystemInfo.isWindows) "win" else "unix"
      val inactivePlatform = if (SystemInfo.isWindows) "unix" else "win"
      val text = manifest.readText().replace("dependencies = []", "dependencies = ['excluded-probe']") + """

        [tool.pixi.feature.docs.pypi-dependencies]
        active-probe = '*'
        [tool.pixi.feature.docs.target.$activePlatform.pypi-dependencies]
        active-target-probe = '*'
        [tool.pixi.feature.docs.target.$inactivePlatform.pypi-dependencies]
        inactive-target-probe = '*'
      """.trimIndent()
      val fixture = codeInsightFixture.get()
      assertThat(manager.getRootDependenciesFile()?.virtualFile).isEqualTo(dependencyFile)
      edtWriteAction { VfsUtil.saveText(dependencyFile, text) }
      withContext(Dispatchers.EDT) {
        fixture.configureFromExistingVirtualFile(dependencyFile)
        fixture.enableInspections(DependenciesInspection())
        fixture.doHighlighting()
      }
      manager.listDeclaredPackagesCached()
      val expected = listOfNotNull(
        PyBundle.message("INSP.dependencies.package.not.installed", "active-probe"),
        PyBundle.message("INSP.dependencies.package.not.installed", "active-target-probe"),
        PyBundle.message("INSP.dependencies.package.not.installed", "excluded-probe").takeUnless { noDefaultFeature },
      )
      waitUntilAssertSucceeds("The inspection must use the selected Pixi environment.") {
        readAction {
          assertThat(fixture.file.findEvoPyProjectIfReady(mainForOrphans = false)?.interpreter?.ref).isEqualTo(interpreter.ref)
          assertThat(manager.tracksDependencyFile(fixture.file)).isTrue()
        }
        val warnings = withContext(Dispatchers.EDT) {
          fixture.doHighlighting().filter { it.severity >= HighlightSeverity.WEAK_WARNING }.map { it.description }
        }
        assertThat(warnings).containsExactlyInAnyOrderElementsOf(expected)
      }
    }
    finally {
      edtWriteAction { FileEditorManagerEx.getInstanceEx(projectFixture.get()).closeAllFiles() }
      pyProject.setPythonInterpreter(null).getOrThrow()
      registry.removePythonInterpreter(pyProject, interpreter)
      tool.setCustomExecutablePath(localEel.descriptor, previous)
    }
  }
}
