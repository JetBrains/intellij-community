// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.packaging

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.python.hatch.HATCH_TOML
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.packaging.management.PythonPackageManagerAction
import com.jetbrains.python.packaging.management.getPythonPackageManager
import kotlin.text.Regex.Companion.escape

internal sealed class HatchPackageManagerAction : PythonPackageManagerAction<HatchPackageManager, String>() {
  override val fileNamesPattern: Regex = """^(${escape(HATCH_TOML)}|${escape(PY_PROJECT_TOML)})$""".toRegex()

  override fun getManager(e: AnActionEvent): HatchPackageManager? = e.getPythonPackageManager()
}

internal class HatchRunAction() : HatchPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: HatchPackageManager): Result<String, PyError> {
    val service = manager.getHatchService().getOr { return it }
    return service.syncDependencies()
  }
}
