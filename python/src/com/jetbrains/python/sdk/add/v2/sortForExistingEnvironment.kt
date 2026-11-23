// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.jetbrains.python.project.asPyProjectCandidate
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus


private val LOG = fileLogger()

/**
 * Python order, see PY-78035
 */
private enum class Group {
  /**
   * SDK venvs, associated with a current module
   */
  ASSOC_WITH_PROJ_ROOT,

  /**
   * Venvs in a user home
   */
  VENVS_IN_USER_HOME,

  /**
   * System (both vanilla and from providers)
   */
  OTHER,
}

internal fun <P : PathHolder> Flow<List<PythonSelectableInterpreter<P>>?>.mapDistinctSortedForExistingEnvironment(
  module: Module?,
): Flow<List<PythonSelectableInterpreter<P>>?> = map { existing ->
  existing ?: return@map null
  val withUniquePath = existing.distinctBy { interpreter -> interpreter.homePath }
  sortForExistingEnvironment(withUniquePath, module)
}

/**
 * Sorts and filters [pythons] to show them in "existing pythons" combobox in v2.
 * If [module] is set, v2 is displayed for the certain module.
 */
@ApiStatus.Internal
suspend fun <P : PathHolder> sortForExistingEnvironment(
  pythons: Collection<PythonSelectableInterpreter<P>>,
  module: Module?,
  venvReader: VirtualEnvReader = VirtualEnvReader(),
): List<PythonSelectableInterpreter<P>> {
  val venvRoot = withContext(Dispatchers.IO) {
    venvReader.getVEnvRootDir()
  }.toString()
  return withContext(Dispatchers.Default) {
    // A detected Python at the path of an existing interpreter is that interpreter, so only the interpreter is listed.
    val existingPaths = pythons.filterIsInstance<ExistingSelectableInterpreter<P>>().mapTo(HashSet()) { it.homePath }
    val withoutCopies = pythons.filter { it is ExistingSelectableInterpreter || it.homePath !in existingPaths }
    val groupedPythons = withoutCopies.groupBy {
      when (it) {
        is InstallableSelectableInterpreter -> error("$it is unexpected")
        is DetectedSelectableInterpreter -> {
          if (module != null) {
            val homePath = it.homePath
            when (homePath) {
              is PathHolder.Eel -> {
                if (ModuleRootManager.getInstance(module).contentRoots.any { root -> homePath.path.startsWith(root.toNioPath()) }) {
                  return@groupBy Group.ASSOC_WITH_PROJ_ROOT
                }
              }
              is PathHolder.Target -> Unit
            }
          }
        }
        is ManuallyAddedSelectableInterpreter -> Unit // Those are pythons, and not SDKs
        is ExistingSelectableInterpreter -> {
          // The interpreter of this module goes first. An interpreter of another Python project stays selectable: a
          // module can use the interpreter of its workspace root or of its parent.
          val moduleDir = module?.asPyProjectCandidate()?.baseDir
          if (moduleDir != null && it.pythonInterpreterWrapper.pythonInterpreter.ownerDir == moduleDir) {
            return@groupBy Group.ASSOC_WITH_PROJ_ROOT
          }
        }
      }
      return@groupBy if (it.homePath.toStringPossiblyMRFS().startsWith(venvRoot)) Group.VENVS_IN_USER_HOME else Group.OTHER
    }
    if (LOG.isDebugEnabled) {
      LOG.debug(groupedPythons.map { (group, pythons) ->
        "$group: (${pythons.joinToString()})"
      }.joinToString("\n"))
    }

    groupedPythons.toSortedMap().flatMap { (_, pythons) ->
      pythons.sorted()
    }.distinctBy { it.homePath }
  }
}
