// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * What the SDK updater measures about an interpreter, and what the user sets for it. [PythonInterpreterDetails] saves the
 * facts, and [PythonInterpreterRoots] derives the roots of the interpreter from them, see [factRootPaths]. No root is a fact.
 */
@ApiStatus.Internal
data class PythonInterpreterFacts(
  /** The version, such as `Python 3.12.4`, or `null` before the first measure. */
  val version: String?,
  /** The local paths of `sys.path`, in its order. A path can be on `sys.path` once only. */
  val sysPath: List<Path>,
  /** The paths that the user added to the interpreter. */
  val addedPaths: List<Path> = emptyList(),
  /** The paths that the user excluded from the interpreter. */
  val excludedPaths: Set<Path> = emptySet(),
)

/**
 * The paths of [facts] that are roots of the interpreter, in their order: `sys.path`, then the added paths. An excluded
 * path, the environment home [environmentHome] and an `.egg-info` directory are no roots. A caller splits them into
 * class roots and the source roots of its module, see [PythonInterpreterRoots].
 *
 * It reads only the paths, so it needs no file system and no lock.
 */
@ApiStatus.Internal
fun factRootPaths(facts: PythonInterpreterFacts, environmentHome: Path?): List<Path> {
  val home = environmentHome?.normalize()
  val excluded = facts.excludedPaths.mapTo(HashSet()) { it.normalize() }
  return (facts.sysPath + facts.addedPaths).map { it.normalize() }.distinct()
    .filter { path -> path !in excluded && path != home && path.fileName?.toString()?.endsWith(".egg-info") != true }
}
