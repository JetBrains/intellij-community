// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.PythonInterpreterRootsContributor
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.typing.PyBundledStubs
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.Locale

/**
 * The bundled stubs of the installed packages of an interpreter, see [PyBundledStubs] and [PyTypeShed]: the stub package
 * of an installed package is a class root, unless the package brings its own stubs. They follow the package list of the
 * manager of the interpreter. An interpreter whose manager has no list yet has none, and the list asks for a new derive
 * when it comes, see [PythonPackageStubRootsListener].
 */
@ApiStatus.Internal
class PythonPackageStubRoots : PythonInterpreterRootsContributor {
  override fun classRoots(project: Project, interpreter: PythonInterpreter): List<Path> {
    val manager = project.service<PythonPackageManagerService>().cachedFor(interpreter) ?: return emptyList()
    return PythonPackageStubs.stubRootPathsOf(manager.listInstalledPackagesSnapshot().mapTo(HashSet()) { it.name })
  }
}

/**
 * Derives the roots of an interpreter again when its installed packages change, see [PythonPackageStubRoots]. The
 * platform registers it with the project, so it hears the first list of each manager.
 */
internal class PythonPackageStubRootsListener(private val project: Project) : PythonPackageManagementListener {
  override fun packagesChanged(interpreter: PythonInterpreter) {
    PythonInterpreterRoots.getInstance(project).requestRefresh(interpreter)
  }
}

/** The bundled stubs of the installed packages, see [PythonPackageStubRoots]. */
@ApiStatus.Internal
object PythonPackageStubs {
  /** The paths of the stub roots of [installed], see [stubRootsOf]. */
  fun stubRootPathsOf(installed: Set<String>): List<Path> = stubRootsOf(installed).mapNotNull { it.toNioPathOrNull() }

  /**
   * The stub roots of [installed]: each bundled stub package whose package is installed, and whose own stubs and typeshed
   * stubs are not.
   */
  @VisibleForTesting
  fun stubRootsOf(installed: Set<String>): List<VirtualFile> =
    listOfNotNull(PyTypeShed.thirdPartyStubRoot, PyBundledStubs.root)
      .flatMap { it.children.asList() }
      .filter { it.isDirectory }
      .filter { stubRoot ->
        val name = stubRoot.name
        name in installed || PyPsiPackageUtil.moduleToPackageName(name, name) in installed
      }
      .filter { stubRoot ->
        "${stubRoot.name.lowercase(Locale.ROOT)}-stubs" !in installed && "types-${stubRoot.name}" !in installed
      }
}
