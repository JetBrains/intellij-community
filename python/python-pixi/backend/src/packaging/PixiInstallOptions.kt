// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.errorProcessing.PyResult

internal data class PixiInstallOptions(val feature: String?, val arguments: List<String>)

internal fun PixiInstallOptions.requiresPixiTable(): Boolean = arguments.any {
  it.substringBefore('=') in setOf("--index", "--platform", "--editable", "--path", "--git", "-p", "-g") ||
  it.startsWith("-p") || it.startsWith("-g")
}

internal fun parsePixiInstallOptions(
  options: List<String>,
  group: PyDependencyGroup?,
  editableLocation: Boolean,
): PyResult<PixiInstallOptions> {
  val features = mutableSetOf<String>()
  group?.name?.let { features.add(if (it == "main") "default" else it) }
  val arguments = mutableListOf<String>()
  val iterator = options.iterator()
  while (iterator.hasNext()) {
    when (val option = iterator.next()) {
      "--feature", "-f" -> {
        if (!iterator.hasNext()) return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.options.invalid"))
        features.add(iterator.next())
      }
      "-e" -> {
        if (!editableLocation) {
          return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.editable.unsupported"))
        }
        arguments.add("--editable")
      }
      else -> when {
        option.substringBefore('=') in setOf("--package", "--manifest-path", "--environment", "--workspace", "--script") ||
        listOf("-m", "-w", "-s").any { option.startsWith(it) } ->
          return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.options.invalid"))
        option.startsWith("--feature=") -> features.add(option.substringAfter('='))
        else -> arguments.add(option)
      }
    }
  }
  if (features.size > 1 || features.any { it.isBlank() || it.startsWith('-') }) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.options.invalid"))
  }
  return PyResult.success(PixiInstallOptions(features.singleOrNull(), arguments))
}
