// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl
import com.intellij.python.community.services.shared.PythonWithLanguageLevel
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Service to register and obtain [SystemPython]s
 */
@ApiStatus.NonExtendable
sealed interface SystemPythonService {
  /**
   * @return system pythons installed on OS in order from highest (hence, the first one is usually the best one)
   */
  suspend fun findSystemPythons(eelApi: EelApi = localEel): List<SystemPython>

  /**
   * When user provides a path to the python binary, use this method to the [SystemPython].
   * @return either [SystemPython] or an error if python is broken.
   */
  suspend fun registerSystemPython(pythonPath: PythonBinary): Result<SystemPython, @Nls String>

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
 *
 * Instances could be obtained with [SystemPythonService]
 */
@JvmInline
value class SystemPython internal constructor(private val impl: PythonWithLanguageLevelImpl) : PythonWithLanguageLevel by impl

/**
 * Tool to install python on OS.
 */
@ApiStatus.NonExtendable
sealed interface PythonInstallerService {

  /**
   * Installs latest stable python on OS.
   * Returns Unit for now (so you should call [SystemPythonService.findSystemPythons]), but this is a subject to change.
   */
  @ApiStatus.Experimental
  suspend fun installLatestPython(): Result<Unit, String>
}