// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.evolution

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.intellij.python.pixi.backend.workspace.PixiRuntime
import com.intellij.python.sdk.backend.evolution.listEntryNames
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal suspend fun createPixiNamedEnvironment(runtime: PixiRuntime, root: Path, name: String, pythonVersion: String): PyResult<Unit> =
  withContext(Dispatchers.IO) {
    if (!name.matches(Regex("[a-z0-9-]+"))) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.name.invalid"))
    }
    if (!pythonVersion.matches(Regex("3\\.[0-9]+"))) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.python.version.invalid"))
    }
    val manifest = root.resolve("pixi.toml").takeIf { it.exists() } ?: root.resolve("pyproject.toml")
    if (parsePixiToml(manifest.readText()) == null) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.manifest.invalid"))
    }
    if (name in pixiReservedEnvironmentNames(root)) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.name.taken", name))
    }
    runtime.executeAndHandleErrors(
      "add", "--no-install", "--feature=$name", "--", "python=$pythonVersion.*",
      transformer = ZeroCodeStdoutTransformer,
    ).getOr { return@withContext it }
    runtime.executeAndHandleErrors(
      "workspace", "environment", "add", "--no-default-feature", "--feature=$name", "--", name,
      transformer = ZeroCodeStdoutTransformer,
    ).mapSuccess { }
  }

internal suspend fun pixiReservedEnvironmentNames(root: Path): List<String> = withContext(Dispatchers.IO) {
  val manifest = root.resolve("pixi.toml").takeIf { it.exists() } ?: root.resolve("pyproject.toml")
  val parsed = parsePixiToml(manifest.readText())
  val table = parsed?.getTable("tool.pixi") ?: parsed
  val environmentsDirectory = root.resolve(".pixi/envs")
  (findPixiWorkspace(root)?.environments.orEmpty() + table?.getTable("feature")?.keySet().orEmpty() +
   if (environmentsDirectory.exists()) listEntryNames(environmentsDirectory) else emptyList()).distinct()
}
