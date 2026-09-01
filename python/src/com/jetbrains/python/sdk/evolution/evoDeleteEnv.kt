// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.evolution

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.fs.EelFileUtils
import com.jetbrains.python.errorProcessing.PyResult
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.resolvePythonBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

private val LOG = fileLogger()

/**
 * Deletes the environment rooted at [dir], and refuses anything that is not one — what a tool with no `remove` command
 * of its own uses to rebuild an environment in place.
 *
 * The refusal is the point rather than a nicety. [dir] descends from a path the frontend echoed back, so a broken round
 * trip must not be able to delete an arbitrary folder, and this is the one call in the widget that destroys something.
 * A directory holding no interpreter is not an environment, whatever it was asked to be.
 *
 * Uses the Eel-aware delete, so a WSL or container environment is removed on the machine holding it rather than walked
 * one entry at a time across the boundary.
 *
 * Catches [IOException] alone, which is what the delete declares. Anything else is not an outcome this knows how to
 * report, so it travels on.
 */
@ApiStatus.Internal
suspend fun deleteEnvDir(dir: Path): PyResult<Unit> = withContext(Dispatchers.IO) {
  if (!dir.isDirectory()) {
    return@withContext PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", dir.pathString))
  }
  if (dir.resolvePythonBinary() == null) {
    return@withContext PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.an.env", dir.pathString))
  }
  try {
    EelFileUtils.deleteRecursively(dir)
  }
  catch (e: IOException) {
    LOG.warn("Evo: failed to delete the environment at $dir", e)
    return@withContext PyResult.localizedError(PySdkBundle.message("evolution.error.env.delete.failed", dir.pathString))
  }
  PyResult.success(Unit)
}
