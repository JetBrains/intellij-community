// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.add

import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.workspace.DEFAULT_ENVIRONMENT
import com.intellij.python.pixi.backend.workspace.PixiWorkspaceOperations
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pixi.backend.workspace.createPixiRuntime
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.pathString

internal suspend fun <P : PathHolder> initializePixiWorkspace(fileSystem: FileSystem<P>, selection: PixiSelection<P>): PyResult<Unit> {
  val manifest = withContext(Dispatchers.IO) {
    val root = selection.workspace.root.createDirectories()
    root.resolve(PY_PROJECT_TOML).takeIf { it.exists() } ?: root.resolve("pixi.toml")
  }
  return PixiWorkspaceOperations.run(selection.workspace.root) {
    initializePixiWorkspace(fileSystem, selection, manifest)
  }
}

private suspend fun <P : PathHolder> initializePixiWorkspace(
  fileSystem: FileSystem<P>,
  selection: PixiSelection<P>,
  manifest: Path,
): PyResult<Unit> {
  val format = if (manifest.fileName.pathString == PY_PROJECT_TOML) "pyproject" else "pixi"
  val runtime = createPixiRuntime(fileSystem, selection.executable, selection.workspace.root)
  runtime.executeAndHandleErrors(
    "init", ".", "--format", format, "--channel", "conda-forge",
    transformer = ZeroCodeStdoutTransformer,
  ).getOr { return it }

  val addArguments = buildList {
    add("add")
    add("--manifest-path")
    add(manifest.fileName.pathString)
    if (selection.environment != DEFAULT_ENVIRONMENT) {
      add("--feature=${selection.environment}")
    }
    add(selection.pythonVersion?.let { "python=$it.*" } ?: "python")
  }
  runtime.executeAndHandleErrors(*addArguments.toTypedArray(), transformer = ZeroCodeStdoutTransformer).getOr { return it }

  if (selection.environment != DEFAULT_ENVIRONMENT) {
    runtime.executeAndHandleErrors(
      "workspace", "environment", "add",
      "--manifest-path", manifest.fileName.pathString,
      "--feature=${selection.environment}", "--", selection.environment,
      transformer = ZeroCodeStdoutTransformer,
    ).getOr { return it }
  }
  return PyResult.success(Unit)
}
