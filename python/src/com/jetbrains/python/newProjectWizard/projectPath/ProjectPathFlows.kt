// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.projectPath

import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.CHECK_NO_RESERVED_WORDS
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.equalsMayBeUsingMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.SystemProperties
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.FileSystems
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
  val projectName: Flow<@NlsSafe String> = projectPath.filterNotNull().map { PyPackageName.normalizeProjectName(it.name) }


  companion object {
    private val defaultPath = Path(SystemProperties.getUserHome())
    private val localRoots by lazy {
      FileSystems.getDefault().rootDirectories.toSet()
    }

    /**
     * Use [fixedPath] as input
     */
    fun create(fixedPath: Path): ProjectPathFlows = ProjectPathFlows(MutableStateFlow(fixedPath))

    /**
     * Use [projectPathString] as input i.e `c:\foo`
     */
    fun create(projectPathString: Flow<String>, onlyAllowPathsOn: EelDescriptor): ProjectPathFlows =
      ProjectPathFlows(projectPathString.map {
        withContext(Dispatchers.Default) {
          validatePath(it, onlyAllowPathsOn).successOrNull
        }
      })


    /**
     * checks that [pathAsString] is a valid path and returns it or error
     */
    fun validatePath(pathAsString: String, onlyAllowPathsOn: EelDescriptor): Result<Path, ProjectFlowValidationError> {
      val path = try {
        Paths.get(pathAsString)
      }
      catch (e: InvalidPathException) {
        return PyResult.failure(ProjectFlowValidationError(e.reason))
      }
      val eelDescriptor = path.getEelDescriptor()
      if (!eelDescriptor.equalsMayBeUsingMachine(onlyAllowPathsOn)) {
        return PyResult.failure(ProjectFlowValidationError(PyBundle.message("python.sdk.new.error.not.supported", eelDescriptor.name),
                                                           wrongEelDescriptor = eelDescriptor))
      }
      if (onlyAllowPathsOn == LocalEelDescriptor && localRoots.none { root -> path.startsWith(root) }) {
        return PyResult.failure(ProjectFlowValidationError(PyBundle.message("python.sdk.new.error.unsupported.root")))
      }

      if (!path.isAbsolute) {
        return PyResult.failure(ProjectFlowValidationError(PyBundle.message("python.sdk.new.error.no.absolute")))
      }

      for (validator in arrayOf(CHECK_NON_EMPTY, CHECK_NO_RESERVED_WORDS)) {
        validator.curry { pathAsString }.validate()?.let {
          return PyResult.failure(ProjectFlowValidationError(it.message))
        }
      }
      return Result.Success(path)
    }
  }
}

/**
 * A special case of [MessageError] when user entered a path on [wrongEelDescriptor]
 */
@ApiStatus.Internal
class ProjectFlowValidationError internal constructor(message: @Nls String, val wrongEelDescriptor: EelDescriptor? = null) :
  MessageError(message)