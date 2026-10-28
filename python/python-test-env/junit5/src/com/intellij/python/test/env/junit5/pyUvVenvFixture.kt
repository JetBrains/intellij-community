// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.jetbrains.python.sdk.ModuleOrProject
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.junit5Tests.framework.env.SdkFixture
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.test.env.uv.getOrDownloadUvExecutable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.setPythonSdkForTests
import com.jetbrains.python.sdk.runExecutableWithProgress
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.skeleton.PySkeletonUtil
import com.jetbrains.python.sdk.uv.UvMode
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.junit5Tests.framework.env.PyInterpreterFixture
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.sitePackagesDirectory

/**
 * Creates a virtual env with `uv venv` and sets uv as the package manager in [mode]. The default is pip mode, because the
 * directory holds no `pyproject.toml`; a test that writes one passes [UvMode.Project]. If [addToSdkTable] then also added
 * to the project jdk table.
 *
 * Similar to [pyVenvFixture] but uses `uv venv` instead of virtualenv helper.
 * UV is significantly faster for venv creation and package installation.
 * UV will be downloaded automatically if not present.
 *
 */
fun TestFixture<SdkFixture<PyEnvironment>>.pyUvVenvFixture(
  addToSdkTable: Boolean,
  moduleFixture: TestFixture<Module>,
  mode: UvMode = UvMode.Pip(),
): TestFixture<Sdk> = testFixture {
  val env = this@pyUvVenvFixture.init().env
  val module = moduleFixture.init()
  val baseDirPath = module.baseDir?.toNioPath() ?: error("Module $module has no base dir")
  val venvDir = baseDirPath.resolve(".venv")


  // Get or download UV executable
  val uvExecutable = getOrDownloadUvExecutable(LATEST_UV_VERSION)
  // Create venv using UV
  runExecutableWithProgress(
    uvExecutable, venvDir.parent, 10.minutes, emptyList(),
    "venv", venvDir.pathString, "--python", env.pythonPath.pathString
  ).getOrThrow()


  // Find Python in created venv
  val venvPython = withContext(Dispatchers.IO) {
    VirtualEnvReader().findPythonInPythonRoot(venvDir)
  } ?: error("Python executable not found in UV venv: $venvDir")

  val interpreter =
    setupExistingEnvAndSdk(moduleOrProject = ModuleOrProject.ModuleAndProject(module), pythonBinary = venvPython, envWorkingDir = baseDirPath, mode = mode).getOrThrow()
  val sdk = interpreter.getSdkAPI()
  if (addToSdkTable) {
    module.setPythonSdkForTests(sdk)
    sdk.setAssociationToModule(module)
  }
  // workaround interesting behavior of VFS_STRUCTURAL_MODIFICATIONS
  PySkeletonUtil.getSitePackagesDirectory(sdk)?.getChildren()

  initialized(sdk) {
    edtWriteAction {
      ProjectJdkTable.getInstance().removeJdk(sdk)
    }
  }
}

/**
 * Creates a virtual env with `uv venv` and sets uv as the package manager in [mode]. The Python project of
 * [pyProjectFixture] gets its interpreter. The default is pip mode; a test that writes a `pyproject.toml` passes
 * [UvMode.Project].
 *
 * Similar to [pyVenvFixture] but uses `uv venv` instead of virtualenv helper.
 * UV is significantly faster for venv creation and package installation.
 * UV will be downloaded automatically if not present.
 *
 */
fun TestFixture<PyInterpreterFixture<PyEnvironment>>.pyUvVenvFixture(
  pyProjectFixture: TestFixture<PyProject>,
  mode: UvMode = UvMode.Pip(),
): TestFixture<PythonInterpreter> = testFixture {
  val interpreterFixture = this@pyUvVenvFixture.init()
  initUvVenvInterpreter(interpreterFixture.env, pyProjectFixture, mode)
}

/** [pyUvVenvFixture] on the test Python alone, which adds no interpreter of its own to the project. */
@JvmName("pyEnvironmentUvVenvFixture")
fun TestFixture<PyEnvironment>.pyUvVenvFixture(
  pyProjectFixture: TestFixture<PyProject>,
  mode: UvMode = UvMode.Pip(),
): TestFixture<PythonInterpreter> = testFixture {
  initUvVenvInterpreter(this@pyUvVenvFixture.init(), pyProjectFixture, mode)
}

private suspend fun TestFixtureInitializer.R<PythonInterpreter>.initUvVenvInterpreter(
  env: PyEnvironment,
  pyProjectFixture: TestFixture<PyProject>,
  mode: UvMode,
): TestFixtureInitializer.InitializedTestFixture<PythonInterpreter> {
  val pyProject = pyProjectFixture.init()
  val baseDirPath = pyProject.baseDir
  val venvDir = baseDirPath.resolve(".venv")


  // Get or download UV executable
  val uvExecutable = getOrDownloadUvExecutable(LATEST_UV_VERSION)

  // Create venv using UV
  runExecutableWithProgress(
    uvExecutable, venvDir.parent, 10.minutes, emptyList(),
    "venv", venvDir.pathString, "--python", env.pythonPath.pathString
  ).getOrThrow()


  // Find Python in created venv
  val venvPython = withContext(Dispatchers.IO) {
    VirtualEnvReader().findPythonInPythonRoot(venvDir)
  } ?: error("Python executable not found in UV venv: $venvDir")

  val interpreter =
    setupExistingEnvAndSdk(moduleOrProject = ModuleOrProject.ModuleAndProject(pyProject), pythonBinary = venvPython, envWorkingDir = baseDirPath, mode = mode).getOrThrow()
  pyProject.setPythonInterpreter(interpreter)
  // workaround interesting behavior of VFS_STRUCTURAL_MODIFICATIONS
  interpreter.sitePackagesDirectory()?.getChildren()
  awaitPythonInterpreterReady(interpreter, pyProject.project)

  return initialized(interpreter) {
    awaitPythonInterpreterUpdateBeforeRemoval(interpreter)
    pyProject.setPythonInterpreter(null)
    PythonInterpreterRegistry.getInstance(pyProject.project).removePythonInterpreter(pyProject, interpreter)
  }
}
