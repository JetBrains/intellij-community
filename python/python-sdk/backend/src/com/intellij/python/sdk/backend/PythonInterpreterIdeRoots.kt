// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.project.Project
import com.intellij.python.community.helpersLocator.PythonBundledPyiStubsLocator
import com.jetbrains.python.psi.LanguageLevel
import java.nio.file.Path
import kotlin.io.path.exists

/** The binary skeletons of the interpreter, when it has them, see [PythonInterpreterRootsContributor]. */
internal class PythonInterpreterSkeletonRoots : PythonInterpreterRootsContributor {
  override fun classRoots(project: Project, interpreter: PythonInterpreter): List<Path> =
    listOfNotNull(interpreter.skeletonsPath?.takeIf { it.exists() })
}

/**
 * The typeshed stdlib of the IDE, which follows the version of the interpreter: Python 2 gets its `@python2` directory
 * first. The version is a fact, so the roots follow it with the facts, see [PythonInterpreterRootsContributor].
 */
internal class PythonInterpreterTypeshedStdlibRoots : PythonInterpreterRootsContributor {
  override fun classRoots(project: Project, interpreter: PythonInterpreter): List<Path> {
    val stdlib = PythonBundledPyiStubsLocator.root().resolve("typeshed").resolve("stdlib")
    val version = PythonInterpreterDetails.getInstance(project).version(interpreter)
    val python2 = version?.let(LanguageLevel::getLanguageLevelFromVersionStringStaticSafe)?.isPython2 == true
    return if (python2) listOf(stdlib.resolve(PYTHON2_STDLIB_DIR), stdlib) else listOf(stdlib)
  }

  private companion object {
    const val PYTHON2_STDLIB_DIR = "@python2"
  }
}
