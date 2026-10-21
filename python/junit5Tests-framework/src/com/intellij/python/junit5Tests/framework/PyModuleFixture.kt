// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import com.intellij.testFramework.junit5.fixture.moduleInProjectFixture
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.application.edtWriteAction
import com.jetbrains.python.project.project
import com.intellij.psi.PsiManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.application.readAction
import kotlin.io.path.exists
import kotlin.io.path.copyToRecursively
import kotlin.io.path.ExperimentalPathApi
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.psi.PsiDirectory
import kotlin.io.path.createDirectories
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import kotlin.io.path.Path

/**
 * Same as [moduleFixture], but with python module type. Use for python tests
 */
@TestOnly
fun TestFixture<Project>.pyModuleFixture(
  pathFixture: TestFixture<Path>,
  addPathToSourceRoot: Boolean = false,
): TestFixture<Module> = moduleFixture(pathFixture, addPathToSourceRoot, PYTHON_MODULE_ID)

/**
 * Same as [moduleFixture], but with python module type. Use for python tests
 */
@TestOnly
fun TestFixture<Project>.pyModuleFixture(
  name: String? = null,
): TestFixture<Module> = moduleFixture(name, PYTHON_MODULE_ID)

/**
 * The [PyProject] of the module this fixture creates.
 *
 * A [PyProject] needs a content root, so build the module from the [TestFixture]<[Path]> overload of [pyModuleFixture].
 * The other overload makes a module with no content root, and this fixture then fails.
 */
@TestOnly
fun TestFixture<Module>.pyProjectFixture(): TestFixture<PyProject> = testFixture("pyProject") {
  val module = this@pyProjectFixture.init()
  val pyProject = requireNotNull(module.asPyProject()) {
    "Module ${module.name} is not a Python project. Build it on a path fixture, so it has a content root."
  }
  initialized(pyProject) {}
}

/**
 * A [PyProject] in [pathFixture]: a Python module whose content root and source root is [pathFixture].
 *
 * By default it is the project root directory, where the main module usually is. A test with several modules gives
 * each one its own directory. Set the interpreter with [setPythonInterpreter]. The module is
 * [PyProject.residesOnModule].
 *
 * The files of [blueprintResourcePath] are copied into [pathFixture] before the module is created, so the first scan
 * of the root sees them.
 */
@OptIn(ExperimentalPathApi::class)
@TestOnly
fun TestFixture<Project>.pyProjectFixture(
  pathFixture: TestFixture<Path> = pathInProjectFixture(Path(".")),
  blueprintResourcePath: Path? = null,
): TestFixture<PyProject> = testFixture("pyProject") {
  val directory = testFixture("pyProjectDirectory") {
    // `pathInProjectFixture(".")` gives `<base>/.`, which would name the module `.` and give it a root of another form.
    val path = pathFixture.init().normalize()
    withContext(Dispatchers.IO) {
      path.createDirectories()
      if (blueprintResourcePath != null) {
        require(blueprintResourcePath.exists()) { "Blueprint resource path does not exist: $blueprintResourcePath" }
        blueprintResourcePath.copyToRecursively(path, followLinks = false, overwrite = true)
      }
    }
    initialized(path) {}
  }
  val project = this@pyProjectFixture.init()
  val pyProject = project.pyProjectAt(directory.init())
  val module = pyProject.residesOnModule
  // A test can remove every module itself, for example before an import builds them again.
  initialized(pyProject) { edtWriteAction { if (!module.isDisposed) ModuleManager.getInstance(project).disposeModule(module) } }
}

/**
 * A [PyProject] in [path]: a new Python module whose content root and source root is [path]. The caller disposes its
 * module, see [PyProject.residesOnModule].
 */
@TestOnly
suspend fun Project.pyProjectAt(path: Path): PyProject {
  withContext(Dispatchers.IO) { path.createDirectories() }
  val module = edtWriteAction { ModuleManager.getInstance(this).newModule(path, PYTHON_MODULE_ID) }
  val root = withContext(Dispatchers.IO) { VfsUtil.findFile(path, true) } ?: error("No $path in VFS")
  ModuleRootModificationUtil.updateModel(module) { it.addContentEntry(root).addSourceFolder(root, false) }
  return requireNotNull(module.asPyProject()) { "Module ${module.name} with a content root is not a Python project" }
}

/** The [PyProject] of the module [moduleName] that the project already has, for example after an import. */
@TestOnly
fun TestFixture<Project>.pyProjectInProjectFixture(moduleName: String): TestFixture<PyProject> = testFixture("pyProject") {
  val module = moduleInProjectFixture(moduleName).init()
  val pyProject = requireNotNull(module.asPyProject()) { "Module $moduleName is not a Python project" }
  initialized(pyProject) {}
}

/**
 * The root of this [PyProject], which [pyProjectFixture] makes a source root. It adds no new root, unlike
 * [sourceRootFixture].
 */
@OptIn(ExperimentalPathApi::class)
@TestOnly
fun TestFixture<PyProject>.rootSourceRootFixture(blueprintResourcePath: Path? = null): TestFixture<PsiDirectory> = testFixture("pyProjectSourceRoot") {
  val pyProject = this@rootSourceRootFixture.init()
  val root = pyProject.baseDir
  if (blueprintResourcePath != null) {
    withContext(Dispatchers.IO) {
      blueprintResourcePath.copyToRecursively(root, followLinks = false, overwrite = true)
      // The copy writes past the VFS, and the root is scanned already.
      VfsUtil.findFile(root, true)?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
    }
  }
  val directory = readAction {
    VfsUtil.findFile(root, false)?.let { PsiManager.getInstance(pyProject.project).findDirectory(it) }
  } ?: error("No source root $root")
  initialized(directory) {}
}

/**
 * A source root in [pathFixture] for the module of this [PyProject]. It is the platform `sourceRootFixture` for
 * [PyProject.residesOnModule].
 */
@TestOnly
fun TestFixture<PyProject>.sourceRootFixture(
  isTestSource: Boolean = false,
  pathFixture: TestFixture<Path> = tempPathFixture(),
  blueprintResourcePath: Path? = null,
): TestFixture<PsiDirectory> {
  val pyProjectFixture = this
  val moduleFixture = testFixture("pyProjectModule") { initialized(pyProjectFixture.init().residesOnModule) {} }
  return moduleFixture.sourceRootFixture(isTestSource, pathFixture, blueprintResourcePath)
}
