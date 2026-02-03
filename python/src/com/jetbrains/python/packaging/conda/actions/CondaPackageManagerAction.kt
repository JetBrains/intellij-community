// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.conda.CondaPackageManager
import com.jetbrains.python.packaging.conda.environmentYml.CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME
import com.jetbrains.python.packaging.conda.environmentYml.CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME
import com.jetbrains.python.packaging.management.PythonPackageManagerAction
import com.jetbrains.python.packaging.management.getPythonPackageManager
import kotlin.text.Regex.Companion.escape

internal sealed class CondaPackageManagerAction : PythonPackageManagerAction<CondaPackageManager, String>() {
  override val fileNamesPattern: Regex = """^(${escape(ENV_YML_FILE_NAME)}|${escape(ENV_YAML_FILE_NAME)})$""".toRegex()

  override fun getManager(e: AnActionEvent): CondaPackageManager? = e.getPythonPackageManager()
}


internal class CondaExportEnvAction() : CondaPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: CondaPackageManager): PyResult<Unit> {
    val envFile = e.getData(PlatformDataKeys.VIRTUAL_FILE) ?: error("Virtual file is not attached")
    return manager.exportEnv(envFile)
  }
}


internal class CondaUpdateEnvAction() : CondaPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: CondaPackageManager): PyResult<Unit> {
    return manager.sync().mapSuccess { }
  }
}