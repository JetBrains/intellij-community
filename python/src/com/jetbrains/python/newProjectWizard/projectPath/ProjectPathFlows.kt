// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.projectPath

import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.CHECK_NO_RESERVED_WORDS
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.SystemProperties
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows.Companion.create
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.name

/**
 * A pack of flows that represents project path changes.
 * Create with [create] that accepts input flow with project path text and constructs other flows out of it.
 */
class ProjectPathFlows private constructor(val projectPath: Flow<Path?>) {

  /**
   * Flow that always emits value. If value is wrong it emits default
   */
  val projectPathWithDefault: Flow<Path> = projectPath.map { it ?: defaultPath }

  /**
   * Flow emits project file name only when project path is valid
   */
  val projectName: Flow<@NlsSafe String> = projectPath.filterNotNull().map { it.name.replace(" ", "_") }


  companion object {
    private val defaultPath = Path(SystemProperties.getUserHome())

    /**
     * Use [fixedPath] as input
     */
    fun create(fixedPath: Path): ProjectPathFlows = ProjectPathFlows(MutableStateFlow(fixedPath))

    /**
     * Use [projectPathString] as input i.e `c:\foo`
     */
    fun create(projectPathString: Flow<String>): ProjectPathFlows = ProjectPathFlows(projectPathString.map {
      withContext(Dispatchers.Default) {
        validatePath(it).successOrNull
      }
    })


    /**
     * checks that [pathAsString] is a valid path and returns it or error
     */
    fun validatePath(pathAsString: String): Result<Path, MessageError> {
      val path = try {
        Paths.get(pathAsString)
      }
      catch (e: InvalidPathException) {
        return failure(e.reason)
      }

      if (!path.isAbsolute) {
        return failure(PyBundle.message("python.sdk.new.error.no.absolute"))
      }

      for (validator in arrayOf(CHECK_NON_EMPTY, CHECK_NO_RESERVED_WORDS)) {
        validator.curry { pathAsString }.validate()?.let {
          return failure(it.message)
        }
      }
      return Result.Success(path)
    }
  }
}