// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.junit5Tests.framework.env.SdkFixture
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.test.env.core.PyEnvironment
import com.intellij.python.venv.createVenv
import com.intellij.python.venv.createVenvAdditionalData
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.junit5Tests.framework.env.PyInterpreterFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry

/**
 * Create virtual env in [where]. If [addToSdkTable] then also added to the project jdk table
 */
fun TestFixture<SdkFixture<PyEnvironment>>.pyVenvFixture(
  where: TestFixture<Path>,
  addToSdkTable: Boolean,
  moduleFixture: TestFixture<Module>? = null,
): TestFixture<Sdk> = testFixture {
  val env = this@pyVenvFixture.init().env
  withContext(Dispatchers.EDT) {
    val module = moduleFixture?.init()
    val workingDirectory = where.init()
    val venvDir = workingDirectory.resolve(".venv")
    val venvPython = createVenv(env.pythonPath, venvDir).getOrThrow()
    val additionalData = createVenvAdditionalData(workingDirectory)
    // The SDK table is global, so a fixture with no module builds its SDK through the default project.
    val project = module?.project ?: ProjectManager.getInstance().defaultProject
    val moduleOrProject = module?.let { ModuleOrProject.ModuleAndProject(it) } ?: ModuleOrProject.ProjectOnly(project)
    // The interpreter is not registered: this old fixture adds its SDK to the table itself.
    val interpreter = createSdk(moduleOrProject, PathHolder.Eel(venvPython), additionalData).orThrow()
    val sdk = interpreter.getSdkAPI()
    if (addToSdkTable) {
      if (module == null) {
        // With no module this fixture stands for a *shared* venv, so it must not keep the association a new SDK
        // derives from its working directory.
        edtWriteAction {
          val modificator = sdk.sdkModificator
          (modificator.sdkAdditionalData as PythonSdkAdditionalData).associatedModulePath = null
          modificator.commitChanges()
        }
      }
      edtWriteAction { ProjectJdkTable.getInstance().addJdk(sdk) }
      if (module != null) {
        module.pythonSdk = sdk
        sdk.setAssociationToModule(module)
      }
    }
    initialized(sdk) {
      edtWriteAction {
        if (ProjectJdkTable.getInstance().findJdk(sdk.name) == sdk) ProjectJdkTable.getInstance().removeJdk(sdk)
      }
    }
  }
}

/**
 * Creates a virtual env in [where] and sets its interpreter on the Python project of [pyProjectFixture]. It returns when
 * the project is indexed with the roots of the new interpreter, so a test can highlight next.
 */
@JvmName("pyEnvironmentVenvFixture")
fun TestFixture<PyEnvironment>.pyVenvFixture(
  where: TestFixture<Path>,
  pyProjectFixture: TestFixture<PyProject>,
): TestFixture<PythonInterpreter> = testFixture {
  initVenvInterpreter(this@pyVenvFixture.init(), where, pyProjectFixture)
}

/** [pyVenvFixture] on the environment of an interpreter fixture, for a test that also uses that interpreter. */
fun TestFixture<PyInterpreterFixture<PyEnvironment>>.pyVenvFixture(
  where: TestFixture<Path>,
  pyProjectFixture: TestFixture<PyProject>,
): TestFixture<PythonInterpreter> = testFixture {
  val interpreterFixture = this@pyVenvFixture.init()
  initVenvInterpreter(interpreterFixture.env, where, pyProjectFixture)
}

private suspend fun TestFixtureInitializer.R<PythonInterpreter>.initVenvInterpreter(
  env: PyEnvironment,
  where: TestFixture<Path>,
  pyProjectFixture: TestFixture<PyProject>,
): TestFixtureInitializer.InitializedTestFixture<PythonInterpreter> {
  val pyProject = pyProjectFixture.init()
  val workingDirectory = where.init()
  val venvDir = workingDirectory.resolve(".venv")
  val venvPython = withContext(Dispatchers.EDT) { createVenv(env.pythonPath, venvDir).getOrThrow() }
  // The venv belongs to the Python project, so its SDK is associated with that project at creation.
  val additionalData = createVenvAdditionalData(pyProject.residesOnModule).getOrThrow()
  val built = createSdk(pyProject, PathHolder.Eel(venvPython), additionalData).orThrow()
  val interpreter = pyProject.setPythonInterpreter(built).orThrow()!!
  awaitPythonInterpreterReady(interpreter, pyProject.project)
  return initialized(interpreter) {
    awaitPythonInterpreterUpdateBeforeRemoval(interpreter)
    pyProject.setPythonInterpreter(null).orThrow()
    PythonInterpreterRegistry.getInstance(pyProject.project).removePythonInterpreter(pyProject, interpreter)
  }
}

/**
 * Creates a virtual env in [where] and gives its Python binary. It adds no interpreter to the project, so the venv is
 * only on disk, as one that the IDE has to detect.
 */
fun TestFixture<PyInterpreterFixture<PyEnvironment>>.pyVenvOnDiskFixture(where: TestFixture<Path>): TestFixture<PythonBinary> =
  testFixture {
    val interpreterFixture = this@pyVenvOnDiskFixture.init()
    val venvDir = where.init().resolve(".venv")
    val venvPython = withContext(Dispatchers.EDT) { createVenv(interpreterFixture.env.pythonPath, venvDir).getOrThrow() }
    initialized(venvPython) {}
  }
