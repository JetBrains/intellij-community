// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.eel.EelOsFamily
import com.intellij.sh.ShStringUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Builds the single shell line that runs a script in a terminal tab.
 *
 * Every path parameter is expected in the spelling of the environment the shell runs in (an `EelPath` string), and
 * [osFamily] is that environment's OS: quoting follows the target shell, not the IDE host. No I/O happens here, so the
 * builder is shared by [ShRunFileAction] (any product mode) and by the run configuration's terminal branch
 * ([ShRunConfigurationProfileState]).
 */
@ApiStatus.Internal
object ShTerminalCommandBuilder {
  /**
   * `KEY=value ... interpreter interpreterOptions scriptPath scriptOptions`; empty parts are skipped, so an empty
   * [interpreterPath] runs the script by its own shebang.
   */
  @JvmStatic
  fun scriptFileCommand(
    interpreterPath: String,
    interpreterOptions: String,
    scriptPath: String,
    scriptOptions: String,
    envs: Map<String, String>,
    osFamily: EelOsFamily,
  ): String {
    val parts = ArrayList<String>()
    parts.addAll(envAssignments(envs, osFamily, endWithSemicolon = false))
    addIfPresent(parts, quotePath(interpreterPath, osFamily))
    addIfPresent(parts, interpreterOptions)
    parts.add(quotePath(scriptPath, osFamily))
    addIfPresent(parts, scriptOptions)
    return parts.joinToString(" ")
  }

  /** `export KEY=value; ... scriptText` */
  @JvmStatic
  fun scriptTextCommand(scriptText: String, envs: Map<String, String>, osFamily: EelOsFamily): String {
    val parts = ArrayList<String>()
    parts.addAll(envAssignments(envs, osFamily, endWithSemicolon = true))
    addIfPresent(parts, scriptText)
    return parts.joinToString(" ")
  }

  /** Quotes a target-spelled path for the target shell; an empty path stays empty. */
  @JvmStatic
  fun quotePath(path: String, osFamily: EelOsFamily): String {
    if (path.isEmpty()) return path
    return when (osFamily) {
      EelOsFamily.Windows -> ShStringUtil.quote(path)
      EelOsFamily.Posix -> quoteIfNeeded(path)
    }
  }

  private fun envAssignments(envs: Map<String, String>, osFamily: EelOsFamily, endWithSemicolon: Boolean): List<String> {
    val result = ArrayList<String>(envs.size)
    for ((index, entry) in envs.entries.withIndex()) {
      val quotedValue = when (osFamily) {
        EelOsFamily.Windows -> quoteIfNeeded(entry.value)
        EelOsFamily.Posix -> ShStringUtil.quote(entry.value)
      }
      if (endWithSemicolon) {
        val semicolon = if (index == envs.size - 1) ";" else ""
        result.add("export ${entry.key}=$quotedValue$semicolon")
      }
      else {
        result.add("${entry.key}=$quotedValue")
      }
    }
    return result
  }

  private fun quoteIfNeeded(value: String): String {
    val escaped = StringUtil.escapeQuotes(value)
    return if (StringUtil.containsWhitespaces(value)) StringUtil.QUOTER.apply(escaped) else escaped
  }

  private fun addIfPresent(parts: MutableList<String>, value: String) {
    if (value.isNotEmpty()) parts.add(value)
  }
}
