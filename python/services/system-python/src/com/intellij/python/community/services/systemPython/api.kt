// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.shared.PythonInfoWithUiComparator
import com.intellij.python.community.services.shared.PythonWithUi
import com.intellij.python.community.services.shared.VanillaPythonWithPythonInfo
import com.intellij.python.community.services.systemPython.impl.PySystemPythonBundle
import com.intellij.python.community.services.systemPython.impl.asSysPythonRegisterError
import com.intellij.python.community.services.systemPython.impl.ensureSystemPython
import com.intellij.python.venv.createVenv
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.mapError
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.venvReader.Directory
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CheckReturnValue

/**
 * Service to register and obtain [SystemPython]s
 */
@ApiStatus.NonExtendable
interface SystemPythonService {
  /**
   * The result of this function might be cached. Use [forceRefresh] to reload it forcibly.
   * @return system pythons installed on OS sorted by type, then by lang.level: in order from highest (hence, the first one is usually the best one)
   */
  suspend fun findSystemPythons(eelApi: EelApi = localEel, forceRefresh: Boolean = false): List<SystemPython>

  /**
   * When user provides a path to the python binary, use this method to the [SystemPython].
   * @return either [SystemPython] or an error if python is broken or not a system python.
   */
  suspend fun registerSystemPython(pythonPath: PythonBinary): Result<SystemPython, SysPythonRegisterError>

  /**
   * @return tool to install python on OS If [eelApi] supports python installation
   */
  fun getInstaller(eelApi: EelApi = localEel): PythonInstallerService?
}

/**
 * System python has an error.
 * It is either [NotASystemPython] (think: virtual env) or [PythonIsBroken] (and completely unusable)
 */
sealed interface SysPythonRegisterError {
  val asPyError: PyError

  /**
   * Virtual env, not a system python
   */
  class NotASystemPython private constructor(val notSystemPython: VanillaPythonWithPythonInfo, override val asPyError: PyError) : SysPythonRegisterError {
    companion object : suspend (VanillaPythonWithPythonInfo) -> NotASystemPython {
      override suspend fun invoke(notSystemPython: VanillaPythonWithPythonInfo): NotASystemPython = NotASystemPython(
        notSystemPython = notSystemPython,
        asPyError = MessageError(PySystemPythonBundle.message("py.system.python.service.python.is.not.system", notSystemPython.getReadableName()))
      )
    }

    override fun toString(): String = "NotASystemPython(notSystemPython=$notSystemPython, asPyError=$asPyError)"

  }

  /**
   * Python failed during execution
   */
  data class PythonIsBroken(override val asPyError: PyError) : SysPythonRegisterError
}


/**
 * Creates an instance of this service
 */
fun SystemPythonService(): SystemPythonService = ApplicationManager.getApplication().service<SystemPythonServiceImpl>()

/**
 * Python installed on OS.
 * [pythonBinary] is guaranteed to be usable and have [languageLevel] at the moment of instance creation.
 * Use [ui] to customize view.
 *
 * When sorted, sorted first by [ui], then by [languageLevel] (from the highest)
 *
 * Instances could be obtained with [SystemPythonService]
 */
class SystemPython private constructor(private val delegate: VanillaPythonWithPythonInfo, override val ui: PyToolUIInfo?) : VanillaPythonWithPythonInfo by delegate, PythonWithUi, Comparable<SystemPython> {

  internal companion object {
    val comparator = PythonInfoWithUiComparator<SystemPython>()
    internal suspend fun create(delegate: VanillaPythonWithPythonInfo, ui: PyToolUIInfo?): Result<SystemPython, SysPythonRegisterError> {
      val isSystemPython = ensureSystemPython(delegate).mapError { it.asSysPythonRegisterError() }.getOr { return it }
      return if (isSystemPython) {
        Result.success(SystemPython(delegate, ui))
      }
      else {
        Result.failure(SysPythonRegisterError.NotASystemPython(delegate))
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SystemPython

    if (delegate != other.delegate) return false
    if (ui != other.ui) return false

    return true
  }

  override fun hashCode(): Int {
    var result = delegate.hashCode()
    result = 31 * result + (ui?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "SystemPython(delegate=$delegate, ui=$ui)"
  }

  override fun compareTo(other: SystemPython): Int = comparator.compare(this, other)
}

/**
 * Tool to install python on OS.
 */
@ApiStatus.NonExtendable
interface PythonInstallerService {

  /**
   * Installs latest stable python on OS.
   * Returns Unit for now (so you should call [SystemPythonService.findSystemPythons]), but this is a subject to change.
   */
  @ApiStatus.Experimental
  suspend fun installLatestPython(
    versionSpecifiers: PyVersionSpecifiers = PyVersionSpecifiers.ANY_SUPPORTED,
  ): Result<Unit, String>
}

/**
 * Finds the first [SystemPython] matching the given [specifiers].
 */
@Internal
fun List<SystemPython>.findMatchingPython(specifiers: PyVersionSpecifiers = PyVersionSpecifiers.ANY_SUPPORTED): SystemPython? =
  firstOrNull { specifiers.isValid(it.pythonInfo.languageLevel) }

/**
 * See [createVenv]
 */
@Internal
@CheckReturnValue
suspend fun createVenvFromSystemPython(
  python: SystemPython,
  venvDir: Directory,
  inheritSitePackages: Boolean = false,
  envReader: VirtualEnvReader = VirtualEnvReader(),
): PyResult<PythonBinary> = createVenv(python.pythonBinary, venvDir, inheritSitePackages, envReader)