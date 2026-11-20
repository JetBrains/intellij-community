// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The [interpreter] as [project] uses it.
 *
 * Two projects can use one interpreter, so the interpreter holds only what is the same for each project: its ref, its
 * owner directory, its binary, its environment and the machine that it runs on. What one project keeps about it is
 * here, such as the stored dependency file and the lifetime of the interpreter in the project. The facts live in
 * [PythonInterpreterDetails] of [project].
 *
 * In the interpreter library flow, see [isInterpreterLibraryFlowEnabled], this is the library that a module names by
 * its library entry, see [PyInterpreterLibraryName]. The deprecated legacy flow has no such library. There it is the
 * Python SDK of the table that a module of [project] uses.
 *
 * Two libraries are equal when they have one project and one interpreter key, see [PythonInterpreterKey].
 */
@ApiStatus.Internal
class PyInterpreterLibrary(val project: Project, val interpreter: PythonInterpreter) {
  /**
   * The dependency file stored for [interpreter] in [project], such as its `requirements.txt` or its `pyproject.toml`,
   * or `null` when none is stored. The default of the package manager applies then. The file is relative to the owner
   * directory of [interpreter] when it is inside it.
   */
  val requirementsPath: Path?
    get() = PythonInterpreterDetails.getInstance(project).requirementsPath(interpreter)

  /**
   * Stores [path] as the dependency file, see [requirementsPath]. A relative [path] is relative to the owner directory of
   * [interpreter]. `null` removes the stored file.
   */
  suspend fun writeRequirementsPath(path: Path?) {
    PythonInterpreterDetails.getInstance(project).writeRequirementsPath(interpreter, path)
  }

  /**
   * The lifetime of [interpreter] in [project], for a cache that holds something for it. It ends when the registry
   * removes [interpreter], when the registry replaces the environment behind its key, or when [project] closes. `null`
   * when [interpreter] has no lifetime of its own, so it lives as long as the caller.
   */
  val lifetime: Disposable?
    get() = interpreter.sdk as? Disposable

  override fun equals(other: Any?): Boolean =
    this === other || (other is PyInterpreterLibrary && project == other.project && interpreter.key == other.interpreter.key)

  override fun hashCode(): Int = 31 * project.hashCode() + interpreter.key.hashCode()

  override fun toString(): String = "PyInterpreterLibrary($interpreter in ${project.name})"
}
