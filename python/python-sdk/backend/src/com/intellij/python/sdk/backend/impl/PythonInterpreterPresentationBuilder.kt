// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend.impl

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.sdk.backend.PySdkBundle.message
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.backend.SystemPythonEnvironment
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.binaryPathString
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.sdk.backend.resolvePythonHome
import com.intellij.python.sdk.backend.targetAdditionalData
import com.intellij.python.sdk.common.PyInterpreterRef
import com.jetbrains.python.sdk.add.v2.PathHolder
import kotlin.io.path.pathString
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
internal fun PythonInterpreter.buildItem(): PyInterpreterItem =
  presentation().let { PyInterpreterItem(ref, it.name, it.suffix, it.description, it.problem, it.icon, it.isPathDerivedName) }

/**
 * What a row draws for this interpreter, without the ref that selects it. For a list of the SDK table, which also shows a
 * broken SDK that no ref names, see [PythonInterpreterPresentation].
 *
 * The name is the env ref, which is the name the tool gives the environment: `.venv` for uv and pip, the environment
 * name for conda and hatch, `in-project` for poetry. Where it runs follows it when that is not this machine, as in
 * `ml · SSH`. The version follows the name. The description is the root of the environment, relative to the user home,
 * so the path appears once. A system interpreter shows its binary instead, see [environmentRoot].
 */
fun PythonInterpreter.presentation(): PythonInterpreterPresentation {
  // One question, asked once: it carries both the version the row shows and the reason it is flagged.
  val info = getPythonInfo()
  val problem = problemFrom(info)

  val sudo = if (sdk.isRunAsRootViaSudo()) "[sudo]" else null
  // The version the interpreter reports about itself, which for an environment that states one is more exact than the
  // SDK's. A flagged interpreter has none to show, and the marker says why instead.
  val version = info.successOrNull?.version
  val secondary = listOfNotNull(sudo, version).joinToString(" ").ifEmpty { null }

  return PythonInterpreterPresentation(
    name = listOfNotNull(envName(), whereItRuns()).joinToString(" · "),
    suffix = secondary,
    description = FileUtil.getLocationRelativeToUserHome(environmentRoot()),
    problem = problem,
    icon = icon(problem).rpcId(),
    isPathDerivedName = false,
  )
}

/**
 * The name of the environment: its env ref, see [PyInterpreterRef.envRef]. An env ref that is an absolute path, such as
 * the binary of a system Python, is the root of the environment relative to the user home instead, as the description.
 */
private fun PythonInterpreter.envName(): @NlsSafe String {
  val envRef = ref.envRef.value
  val isAbsolute = envRef.startsWith("/") || envRef.toNioPathOrNull()?.isAbsolute == true
  return if (isAbsolute) FileUtil.getLocationRelativeToUserHome(environmentRoot()) else envRef
}

/**
 * Where this interpreter runs, or `null` for this machine: the type of its target, such as SSH, or the machine of its
 * binary, such as a WSL distribution. A native ref names an interpreter on any Eel machine, so the binary tells which.
 */
private fun PythonInterpreter.whereItRuns(): @Nls String? = when (this) {
  is TargetPythonInterpreter -> targetAdditionalData?.targetEnvironmentConfiguration?.getTargetType()?.displayName
  is NativePythonInterpreter -> pythonBinary.getEelDescriptor().takeIf { it != LocalEelDescriptor }?.let { machineName(it) }
}

/** The user-readable name of [descriptor], such as `Wsl: Ubuntu`. It names a machine, so it is not translated. */
private fun machineName(descriptor: EelDescriptor): @NlsSafe String = descriptor.name

/**
 * The path that tells this interpreter apart: the root of its environment, the directory that holds `bin` or `Scripts`.
 * A system interpreter has no environment of its own, and its root is a shared directory such as `/usr`, so it is told
 * apart by its binary, such as `/usr/bin/python3.13`.
 */
private fun PythonInterpreter.environmentRoot(): String = when (this) {
  is NativePythonInterpreter ->
    if (detectedEnvironment is SystemPythonEnvironment) pythonBinary.pathString else pythonBinary.resolvePythonHome().pathString
  is TargetPythonInterpreter -> (PathHolder.Target(binaryPathString).resolvePythonHome() as PathHolder.Target).pathString
}

/**
 * What a row draws for an SDK that names no interpreter, see [com.intellij.python.sdk.backend.pythonInterpreterResult].
 * The row is `[invalid]`, and [reason] says why. The user can only remove or recreate such an SDK.
 */
fun brokenSdkPresentation(sdk: Sdk, reason: @Nls String): PythonInterpreterPresentation = PythonInterpreterPresentation(
  name = sdk.name,
  suffix = null,
  description = sdk.homePath.orEmpty(),
  problem = invalidProblem(reason),
  icon = wrapIconWithWarningDecorator(AllIcons.Nodes.Unknown).rpcId(),
  isPathDerivedName = isNameDerivedFromHomePath(sdk.name, sdk.homePath),
)

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

/**
 * What an interpreter row draws: the parts of [PyInterpreterItem] other than its ref. A list of the SDK table holds this,
 * because a broken SDK has no ref.
 */
class PythonInterpreterPresentation(
  val name: @NlsSafe String,
  val suffix: @NlsSafe String?,
  val description: @NlsSafe String,
  val problem: PythonInterpreterProblem?,
  val icon: IconId,
  val isPathDerivedName: Boolean,
)
