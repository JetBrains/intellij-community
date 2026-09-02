// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.sdk.backend.PySdkBundle.message
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterPresentationProvider
import com.intellij.python.sdk.backend.asInterpreterRef
import com.intellij.python.sdk.backend.getPythonInfo
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.python.sdk.common.PythonInterpreterProblem
import com.intellij.ui.LayeredIcon
import com.intellij.util.SystemProperties
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.isRunAsRootViaSudo
import com.jetbrains.python.sdk.pySdkAdditionalData
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Everything a renderer needs about this interpreter.
 *
 * Reads only what is already recorded about the interpreter, so building this runs nothing and needs no coroutine.
 * Detecting the environment does; that happened when the [PythonInterpreter] was obtained.
 */
internal fun PythonInterpreter.buildItem(customName: String? = null): PyInterpreterItem {
  // One question, asked once: it carries both the version the row shows and the reason it is flagged.
  val info = getPythonInfo()
  val problem = problemFrom(info)

  val sdk = sdk
  val sudo = if (sdk.isRunAsRootViaSudo()) "[sudo]" else null
  // The version the interpreter reports about itself, which for an environment that states one is more exact than the
  // SDK's. A flagged interpreter has none to show, and the marker says why instead.
  val version = info.successOrNull?.version
  val secondary = listOfNotNull(sudo, version).joinToString(" ").ifEmpty { null }

  // The tool that owns the interpreter may write its short label, where the shortened path reads badly. It replaces only
  // the default: a caller that passed a [customName] named this one rendering, and keeps that name everywhere.
  val toolShortName = if (customName == null) PythonInterpreterPresentationProvider.shortNameFor(sdk) else null

  val displayName = customName ?: sdk.name
  // Only the default path-derived name is safe to compact via the basename heuristic;
  // a custom label like `SSH (sftp://...)` or a caller-supplied [customName] must be
  // rendered as-is (modulo middle ellipsis) so it doesn't degenerate into `python)`.
  val isPathDerivedName = customName == null && isNameDerivedFromHomePath(displayName, sdk.homePath)

  return PyInterpreterItem(
    ref = sdk.asInterpreterRef(),
    name = displayName,
    suffix = secondary,
    // Empty when the SDK carries no binary path. That SDK is invalid anyway, and the row states why through [problem],
    // so there is nothing to gain from a placeholder that reads like a path.
    description = sdk.homePath.orEmpty(),
    problem = problem,
    icon = icon(problem).rpcId(),
    isPathDerivedName = isPathDerivedName,
    toolShortName = toolShortName,
  )
}

/** A configuration the user has to fix, which is `[invalid]` like any other. */
private fun invalidProblem(reason: @Nls String) = PythonInterpreterProblem(message("python.sdk.problem.invalid"), reason)

/** The marker and reason a verdict carries: why it cannot be used, or that its version is not supported. */
private fun problemFrom(info: PyResult<PythonInfo>): PythonInterpreterProblem? = when (info) {
  is Result.Failure -> invalidProblem(info.error.message)
  is Result.Success -> info.result.languageLevel
    .takeIf { it !in LanguageLevel.SUPPORTED_LEVELS }
    ?.let { PythonInterpreterProblem(message("python.sdk.problem.unsupported"), message("python.sdk.problem.unsupported.reason", it)) }
}

/**
 * Mirrors the two branches of `PythonSdkType.suggestSdkName` without re-running its filesystem
 * probe:
 *  - system Python: `name` equals `homePath` (after expanding `~` from
 *    `FileUtil.getLocationRelativeToUserHome`);
 *  - venv / conda / similar: `name` is the env root, `homePath` is the binary inside it
 *    (`<root>/bin/python` or `<root>\Scripts\python.exe`), so `homePath` starts with `name`
 *    as a directory prefix.
 *
 * If neither holds, `name` is a free-form label (remote-SDK label or caller-supplied custom name)
 * and must not be passed through the basename heuristic in [com.intellij.python.sdk.common.shortenPath] (PY-89560).
 */
internal fun isNameDerivedFromHomePath(name: String, homePath: String?): Boolean {
  if (homePath == null || name.isEmpty()) return false

  val expandedName = if (name.startsWith("~/") || name.startsWith("~\\")) {
    SystemProperties.getUserHome() + name.substring(1)
  }
  else name

  val ignoreCase = !SystemInfoRt.isFileSystemCaseSensitive

  // Compare separator-insensitively. `name` is produced by `PythonSdkType.suggestSdkName` through a
  // `Path.toString()` round-trip, so it uses the OS separator (`\` on Windows), while `homePath` may
  // be stored with `/` (e.g. when it originates from EEL/nio). A raw byte compare then fails on
  // Windows even though both denote the same location, and the interpreter widget renders the full
  // path instead of the env basename. This is pure string work — no filesystem access, EDT-safe
  // (`Path.of`/`startsWith` are avoided: they are OS-coupled and throw on non-path labels like
  // `SSH (sftp://...)` that this function also receives).
  val nName = expandedName.replace('\\', '/')
  val nHomePath = homePath.replace('\\', '/')

  return when {
    // System Python: expanded `name` is exactly the binary path.
    nName.equals(nHomePath, ignoreCase) -> true
    // Venv / conda: `name` is the env root, `homePath` is `<root>/bin/python` or `<root>/Scripts/python.exe`.
    nHomePath.length <= nName.length -> false
    !nHomePath.regionMatches(0, nName, 0, nName.length, ignoreCase) -> false
    else -> nHomePath[nName.length] == '/'
  }
}

/**
 * The flavor icon, crossed out when [problem] says the interpreter cannot be used.
 *
 * @see PythonInterpreter.getPythonInfo
 * @see LanguageLevel.SUPPORTED_LEVELS
 */
private fun PythonInterpreter.icon(problem: PythonInterpreterProblem?): Icon {
  val icon = sdk.pySdkAdditionalData.flavor.icon
  return if (problem == null) icon else wrapIconWithWarningDecorator(icon)
}

private fun wrapIconWithWarningDecorator(icon: Icon): LayeredIcon = LayeredIcon(2).apply {
  setIcon(icon, 0)
  setIcon(AllIcons.Actions.Cancel, 1)
}
