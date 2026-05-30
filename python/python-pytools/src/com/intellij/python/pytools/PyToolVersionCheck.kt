// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.openapi.util.text.StringUtil
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.pytools.PyToolsBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

class VersionFormatException : Exception()

data class Version(val value: String) {
  override fun toString(): String = value

  companion object {
    fun parse(versionString: String): Version = Version(versionString)
  }
}

internal fun String.parseVersion(toolVersionPrefix: String): PyResult<Version> {
    val pattern = "^$toolVersionPrefix,?(?:\\s\\(?version)?\\s([^\\s)]+).*$".toRegex(RegexOption.IGNORE_CASE)
  // Tools may print extra lines around the version banner:
  //   - Black appends runtime info after the version line
  //   - Pyright prints upgrade warnings before the version line
  // matchEntire on the full output fails because `.*$` does not span newlines, so try every
  // non-blank line and pick the first one that matches the expected `<tool> <version>` shape.
  val matchResult = lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .firstNotNullOfOrNull { pattern.matchEntire(it) }
  return if (matchResult != null) {
    val (versionString) = matchResult.destructured
    try {
      PyResult.success(Version.parse(versionString))
    }
    catch (ex: VersionFormatException) {
      PyResult.localizedError(ex.localizedMessage)
    }
  }
  else {
    val versionPresentation = StringUtil.shortenTextWithEllipsis(this, 250, 0, true)
    PyResult.localizedError(message("selected.tool.is.wrong", toolVersionPrefix.trim(), versionPresentation))
  }
}
/**
 * Runs `<binary> --version` and verifies that the leading token matches [toolVersionPrefix],
 * returning the parsed [Version] on success or a localized error otherwise. Used to verify a
 * user-supplied tool path actually points at the expected tool.
 *
 * Originally lived in `intellij.python.community.impl`; moved here so all PyTool consumers
 * (settings, run configurations, package managers) can share the same parsing logic.
 */
@ApiStatus.Internal
suspend fun BinaryToExec.getToolVersion(toolVersionPrefix: String): PyResult<Version> {
  val version = withContext(Dispatchers.IO) {
    ExecService().execGetStdout(this@getToolVersion, Args("--version"))
  }.getOr { return it }
  return version.parseVersion(toolVersionPrefix)
}

/**
 * Convenience: validate a user-supplied custom path for [this] tool against its known names.
 * Returns a localized failure if `<path> --version` does not match.
 *
 * The expected version-prefix is derived from [path]'s basename matched against [PyTool.aliases] —
 * required for tools where the primary [PyTool.packageName] differs from the actually-installed
 * alias (e.g. Pyright is integrated as `pyright` but `basedpyright --version` prints
 * `basedpyright X.Y.Z`, which would never match the `pyright` prefix). When the basename
 * matches no known alias we fall back to the primary [PyTool.packageName] for backward compatibility.
 */
@ApiStatus.Internal
suspend fun PyTool.validateCustomPath(path: Path): PyResult<Version> {
  val baseName = path.fileName.toString().removeSuffix(".exe")
  val versionPrefix = aliases.firstOrNull { it.name == baseName }?.name ?: packageName.name
  return BinOnEel(path).getToolVersion(versionPrefix)
}
