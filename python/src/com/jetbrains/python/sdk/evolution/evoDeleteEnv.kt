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
import java.nio.file.AccessDeniedException
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
 *
 * An [AccessDeniedException] is told apart from the rest, because it means something a different message can act on —
 * `evolution.error.env.in.use`. On Windows a running process's own executable cannot be deleted at all, so an
 * interpreter that is still running takes its whole environment with it: everything around `python.exe` goes, and the
 * directory then refuses to. The platform's delete already retries, clears the read-only attribute and runs a GC before
 * giving up (`FileUtilRt.doDelete`), so reaching here is not a transient lock to retry — it is a live handle.
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
  catch (e: AccessDeniedException) {
    LOG.warn("Evo: the environment at $dir is held open, so it was not deleted", e)
    return@withContext PyResult.localizedError(PySdkBundle.message("evolution.error.env.in.use", dir.pathString))
  }
  catch (e: IOException) {
    LOG.warn("Evo: failed to delete the environment at $dir", e)
    return@withContext PyResult.localizedError(PySdkBundle.message("evolution.error.env.delete.failed", dir.pathString))
  }
  PyResult.success(Unit)
}
