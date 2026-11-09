// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.projectPath

import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.CHECK_NO_RESERVED_WORDS
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.equalsMayBeUsingMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.venvReader.Directory
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
import kotlin.io.path.name

/**
 * A pack of flows that represents project path changes. Create it with one of the two [create] functions:
 * - With a fixed path, [T] is [Path]: the path never changes, and it is always valid.
 * - With a flow of path text, [T] is `Path?`: [projectPath] emits null for a text that is not a valid path.
 */
sealed class ProjectPathFlows<T : Path?> private constructor(
  /**
   * Emits the project path. If [T] is nullable, it emits null for a path that is not valid.
   */
  val projectPath: Flow<T>,
) {
  /**
   * The project path is always valid, thus [projectPathWithDefault] does not need a default path.
   */
  private class Fixed(path: Path) : ProjectPathFlows<Path>(MutableStateFlow(path)) {
    override val projectPathWithDefault: Flow<Path> = projectPath
  }

  /**
   * The project path comes from a text, thus it can be not valid. Then [projectPathWithDefault] emits the home directory
   * of the user on [eelForDefaultPath]. A binary on that eel can use it as its working directory.
   */
  private class Dynamic(projectPath: Flow<Path?>, eelForDefaultPath: EelDescriptor) : ProjectPathFlows<Path?>(projectPath) {

    /**
     * The home directory of the user on [eelForDefaultPath]. It is null until [projectPathWithDefault] needs it for the first time.
     */
    @Volatile
    private var defaultPath: Directory? = null

    override val projectPathWithDefault: Flow<Path> = projectPath.map {
      it
      ?: defaultPath
      ?: eelForDefaultPath.toEelApi().fs.user.home.asNioPath().also { userHome ->
        defaultPath = userHome
      }
    }
  }

  /**
   * Emits a path for each value of [projectPath]. For a path that is not valid, it emits a default path.
   * The default path is on the same eel as a valid project path.
   */
  abstract val projectPathWithDefault: Flow<Path>

  /**
   * Emits the project name for each valid [projectPath]. It emits nothing for a path that is not valid.
   */
  val projectName: Flow<@NlsSafe String> = projectPath.filterNotNull().map { PyPackageName.normalizeProjectName(it.name) }


  companion object {
    private val localRoots by lazy {
      FileSystems.getDefault().rootDirectories.toSet()
    }

    /**
     * Use [fixedPath] as the project path. It never changes.
     */
    fun create(fixedPath: Path): ProjectPathFlows<Path> = Fixed(fixedPath)

    /**
     * Use [projectPathString] as input, i.e. `c:\foo`.
     * A text that is not a valid path or that is not on [onlyAllowPathsOn] becomes null in [projectPath].
     */
    fun create(projectPathString: Flow<String>, onlyAllowPathsOn: EelDescriptor): ProjectPathFlows<Path?> =
      Dynamic(projectPathString.map {
        withContext(Dispatchers.Default) {
          validatePath(it, onlyAllowPathsOn).successOrNull
        }
      }, eelForDefaultPath = onlyAllowPathsOn)


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