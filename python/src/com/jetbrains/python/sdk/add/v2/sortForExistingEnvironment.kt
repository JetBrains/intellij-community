// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.isAssociatedWithAnotherModule
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path


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
   * SDK venvs no associated with any project
   */
  SHARED_VENVS,

  /**
   * Venvs in a user home
   */
  VENVS_IN_USER_HOME,

  /**
   * System (both vanilla and from providers)
   */
  OTHER,

  /**
   * Those, which should be filtered out
   */
  REDUNDANT
}

/**
 * Sorts and filters [pythons] to show them in "existing pythons" combobox in v2.
 * If [module] is set, v2 is displayed for the certain module.
 */
@ApiStatus.Internal
suspend fun sortForExistingEnvironment(
  pythons: Collection<PythonSelectableInterpreter>,
  module: Module?,
  venvReader: VirtualEnvReader = VirtualEnvReader.Companion.Instance,
): List<PythonSelectableInterpreter> {
  val venvRoot = withContext(Dispatchers.IO) {
    venvReader.getVEnvRootDir()
  }
  return withContext(Dispatchers.Default) {
    val groupedPythons = pythons.groupBy {
      when (it) {
        is InstallableSelectableInterpreter -> error("$it is unexpected")
        is DetectedSelectableInterpreter, is ManuallyAddedSelectableInterpreter -> Unit // Those are pythons, and not SDKs
        is ExistingSelectableInterpreter -> { //SDKs
          if (module != null) {
            if (it.sdk.isAssociatedWithModule(module)) {
              return@groupBy Group.ASSOC_WITH_PROJ_ROOT
            }
            else if (it.sdk.isAssociatedWithAnotherModule(module)) {
              return@groupBy Group.REDUNDANT // Foreign SDK
            }
          }
          else if (it.sdk.getOrCreateAdditionalData().associatedModulePath != null) {
            // module == null, associated path != null: associated sdk can't be used without a module
            return@groupBy Group.REDUNDANT
          }
          if (it.sdk.associatedModulePath == null) { // Shared SDK
            return@groupBy Group.SHARED_VENVS
          }
        }
      }
      return@groupBy if (Path.of(it.homePath).startsWith(venvRoot)) Group.VENVS_IN_USER_HOME else Group.OTHER
    }
    if (LOG.isDebugEnabled) {
      LOG.debug(groupedPythons.map { (group, pythons) ->
        "$group: (${pythons.joinToString()})"
      }.joinToString("\n"))
    }

    groupedPythons.filterKeys { it != Group.REDUNDANT }.toSortedMap().flatMap { (_, pythons) ->
      pythons.sorted()
    }.distinctBy { it.homePath }
  }
}

