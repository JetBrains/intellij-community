// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.impl.venv.createVenv
import com.intellij.python.community.services.shared.LanguageLevelWithUiComparator
import com.intellij.python.community.services.shared.PythonWithUi
import com.jetbrains.python.PyToolUIInfo
import com.intellij.python.community.services.shared.VanillaPythonWithLanguageLevel
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
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
   * @return either [SystemPython] or an error if python is broken.
   */
  suspend fun registerSystemPython(pythonPath: PythonBinary): PyResult<SystemPython>

  /**
   * @return tool to install python on OS If [eelApi] supports python installation
   */
  fun getInstaller(eelApi: EelApi = localEel): PythonInstallerService?
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
class SystemPython internal constructor(private val delegate: VanillaPythonWithLanguageLevel, override val ui: PyToolUIInfo?) : VanillaPythonWithLanguageLevel by delegate, PythonWithUi, Comparable<SystemPython> {

  private companion object {
    val comparator = LanguageLevelWithUiComparator<SystemPython>()
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
  suspend fun installLatestPython(): Result<Unit, String>
}

/**
 * See [createVenv]
 */
@Internal
@CheckReturnValue
suspend fun createVenvFromSystemPython(
  python: SystemPython,
  venvDir: Directory,
  inheritSitePackages: Boolean = false,
  envReader: VirtualEnvReader = VirtualEnvReader.Instance,
): PyResult<PythonBinary> = createVenv(python.pythonBinary, venvDir, inheritSitePackages, envReader)