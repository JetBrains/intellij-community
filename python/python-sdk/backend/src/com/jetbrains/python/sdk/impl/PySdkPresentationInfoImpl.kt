package com.jetbrains.python.sdk.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.LayeredIcon
import com.intellij.util.SystemProperties
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonInterpreterPresentation
import com.jetbrains.python.sdk.isRunAsRootViaSudo
import com.jetbrains.python.sdk.isSdkSeemsValid
import com.jetbrains.python.sdk.pySdkAdditionalData
import javax.swing.Icon

private const val ELLIPSIS = "\u2026"
private val VERSION_NUMBER_RE = Regex("""\d+\.\S+""")

internal fun Sdk.buildPresentationInfo(customName: String? = null): PythonInterpreterPresentation {
  val modifier = when {
    !isSdkSeemsValid -> "invalid"
    !LanguageLevel.SUPPORTED_LEVELS.contains(PySdkUtil.getLanguageLevelForSdk(this)) -> "unsupported"
    else -> null
  }

  val sudo = if (isRunAsRootViaSudo()) "[sudo]" else null
  val version = versionString?.let { VERSION_NUMBER_RE.find(it)?.value }
  val secondary = listOfNotNull(sudo, version).joinToString(" ").ifEmpty { null }

  val displayName = customName ?: name
  // Only the default path-derived name is safe to compact via the basename heuristic;
  // a custom label like `SSH (sftp://...)` or a caller-supplied [customName] must be
  // rendered as-is (modulo middle ellipsis) so it doesn't degenerate into `python)`.
  val isPathDerivedName = customName == null && isNameDerivedFromHomePath(displayName, homePath)

  return PythonInterpreterPresentation(
    name = displayName,
    suffix = secondary,
    description = homePath ?: "[invalid]",
    modifier = modifier,
    icon = icon(this),
    isPathDerivedName = isPathDerivedName,
  )
}

internal fun shortenPath(path: String, maxLength: Int, keepPrefix: Boolean): String {
  val normalized = path.trimEnd { it == '/' || it == '\\' }
  val lastSeparatorIndex = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
  if (keepPrefix) {
    if (path.length <= maxLength) return path
    if (lastSeparatorIndex > 0) {
      val lastSegment = normalized.substring(lastSeparatorIndex)
      val availableForPrefix = maxLength - lastSegment.length - 1
      if (availableForPrefix > 0) {
        return normalized.substring(0, availableForPrefix) + ELLIPSIS + lastSegment
      }
    }
  }
  if (lastSeparatorIndex < 0) return path.takeLast(maxLength)
  val lastSegment = normalized.substring(lastSeparatorIndex + 1)
  return if (lastSegment.length <= maxLength) lastSegment else ELLIPSIS + lastSegment.takeLast((maxLength - 1).coerceAtLeast(0))
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
 * and must not be passed through the basename heuristic in [shortenPath] (PY-89560).
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
 * Returns an icon to be used as the sdk's icon.
 *
 * Result is wrapped with [AllIcons.Actions.Cancel]
 * if the sdk is local and does not exist, or remote and incomplete or has invalid credentials, or is not supported.
 *
 * @see isSdkSeemsValid
 * @see LanguageLevel.SUPPORTED_LEVELS
 */
private fun icon(sdk: Sdk): Icon {
  val flavor = sdk.pySdkAdditionalData.flavor
  val icon = flavor.icon

  return when {
    !sdk.isSdkSeemsValid ||
    !LanguageLevel.SUPPORTED_LEVELS.contains(PySdkUtil.getLanguageLevelForSdk(sdk)) -> wrapIconWithWarningDecorator(icon)
    //sdk is PyDetectedSdk -> IconLoader.getTransparentIcon(icon)
    else -> icon
  }
}

private fun wrapIconWithWarningDecorator(icon: Icon): LayeredIcon = LayeredIcon(2).apply {
  setIcon(icon, 0)
  setIcon(AllIcons.Actions.Cancel, 1)
}
