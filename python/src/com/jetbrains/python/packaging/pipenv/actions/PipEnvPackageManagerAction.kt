// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pipenv.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.management.PythonPackageManagerAction
import com.jetbrains.python.packaging.management.getPythonPackageManager
import com.jetbrains.python.packaging.pipenv.PipEnvPackageManager
import com.jetbrains.python.sdk.pipenv.PipEnvFileHelper
import kotlin.text.Regex.Companion.escape

internal sealed class PipEnvPackageManagerAction : PythonPackageManagerAction<PipEnvPackageManager, String>() {
  override val fileNamesPattern: Regex = """^(${escape(PipEnvFileHelper.PIP_FILE)}|${escape(PipEnvFileHelper.PIP_FILE_LOCK)})$""".toRegex()

  override fun getManager(e: AnActionEvent): PipEnvPackageManager? = e.getPythonPackageManager()
}


internal class PipEnvLockAction() : PipEnvPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: PipEnvPackageManager): PyResult<Unit> {
    return manager.lock().mapSuccess { }
  }
}


internal class PipEnvInstallEnvAction() : PipEnvPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: PipEnvPackageManager): PyResult<Unit> {
    return manager.sync().mapSuccess { }
  }
}