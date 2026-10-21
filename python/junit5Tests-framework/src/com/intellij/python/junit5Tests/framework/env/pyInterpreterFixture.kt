// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * An interpreter with environment info. Env fixtures (venv, conda) might use [env] and [project].
 */
@ApiStatus.Internal
class PyInterpreterFixture<ENV : Any>(val interpreter: PythonInterpreter, val env: ENV, val project: Project) {
  override fun equals(other: Any?): Boolean {
    return this === other || other is PyInterpreterFixture<*> && other.interpreter == interpreter
  }

  override fun hashCode(): Int = interpreter.hashCode()
}

/**
 * Creates a mock interpreter of this [PyProject]: not a real python, but with [homePath]. It is added through
 * [PythonInterpreterRegistry]. The SDK has [PythonSdkAdditionalData], because the product treats a Python SDK
 * without it as broken.
 */
@JvmName("pyProjectMockInterpreterFixture")
fun TestFixture<PyProject>.pyMockInterpreterFixture(homePath: TestFixture<Path>): TestFixture<PythonInterpreter> = testFixture {
  val pyProject = this@pyMockInterpreterFixture.init()
  val registry = PythonInterpreterRegistry.getInstance(pyProject.project)
  val interpreter = registry.addMockPythonInterpreter(pyProject, createMockSdk(pyProject.project, homePath.init()))
  initialized(interpreter) {
    registry.removePythonInterpreter(pyProject, interpreter)
  }
}

/**
 * Creates a mock interpreter that [ownerDir] owns, and adds no module for it. Use it in a test that counts the modules
 * or the PyProjects of the project, which the module of [pyMockInterpreterFixture] would add to.
 */
fun TestFixture<Project>.pyMockInterpreterWithoutModuleFixture(
  homePath: TestFixture<Path>,
  ownerDir: TestFixture<Path>,
): TestFixture<PythonInterpreter> = testFixture {
  val project = this@pyMockInterpreterWithoutModuleFixture.init()
  val registry = PythonInterpreterRegistry.getInstance(project)
  val interpreter = registry.addMockPythonInterpreter(ownerDir.init(), createMockSdk(project, homePath.init()))
  initialized(interpreter) {
    registry.removeTestInterpreter(interpreter)
  }
}

private suspend fun createMockSdk(project: Project, path: Path): Sdk {
  val sdk = ProjectJdkTable.getInstance(project).createSdk("PyMockSDK" + System.currentTimeMillis().toString(), PyMockSdkTypeId)
  val root = withContext(Dispatchers.IO) { VfsUtil.findFile(path, true) } ?: error("No $path")
  edtWriteAction {
    val modificator = sdk.sdkModificator
    modificator.homePath = path.pathString
    modificator.addRoot(root, OrderRootType.CLASSES)
    modificator.sdkAdditionalData = PythonSdkAdditionalData(null)
    modificator.commitChanges()
  }
  return sdk
}
