// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes


/**
 * "implementation" for pip and poetry tools.
 * [toolName] (i.e `poetry`).
 * [getAdditionalSearchPaths] additional paths to look for [toolName] on a certain eel
 */
internal data class ToolCommandExecutor(
  private val toolName: @NlsSafe String,
  private val getAdditionalSearchPaths: EelApi.() -> List<Path> = { emptyList() },
  private val getToolPathFromSettings: PropertiesComponent.() -> @SystemIndependent String?,
) {

  /**
   * Detects the executable in `$PATH`.
   */
  suspend fun detectToolExecutable(eel: EelApi): PyResult<Path> = detectTool(toolName, eel, additionalSearchPaths = getAdditionalSearchPaths(eel))

  /**
   * Returns the configured executable or detects it automatically.
   */
  suspend fun getToolExecutable(eel: EelApi): PyResult<Path> {
    val toolPathFromSettings = getToolPathFromSettings(PropertiesComponent.getInstance())?.let { Path.of(it) }
    if (toolPathFromSettings != null && toolPathFromSettings.getEelDescriptor() == eel.descriptor) {
      return PyResult.success(toolPathFromSettings)
    }
    return detectToolExecutable(eel)
  }
}

internal fun ToolCommandExecutor.detectToolExecutableOrNull(eel: EelApi): Path? {
  return runBlockingCancellable { detectToolExecutable(eel) }.getOrNull()
}

internal suspend fun <T> ToolCommandExecutor.runTool(dirPath: Path?, vararg args: String, transformer: ProcessOutputTransformer<T>): PyResult<T> {
  val executable = getToolExecutable(dirPath?.getEelDescriptor()?.toEelApi() ?: localEel).getOr { return it }
  return runExecutableWithProgress(executable, dirPath, 10.minutes, args = args, transformer = transformer)
}

internal suspend fun ToolCommandExecutor.runTool(dirPath: Path?, vararg args: String): PyResult<String> =
  runTool(dirPath, args = args, transformer = ZeroCodeStdoutTransformer)


