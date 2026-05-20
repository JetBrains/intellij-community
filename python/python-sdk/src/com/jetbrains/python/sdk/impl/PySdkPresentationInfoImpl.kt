package com.jetbrains.python.sdk.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.LayeredIcon
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonInterpreterPresentation
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.isRunAsRootViaSudo
import com.jetbrains.python.sdk.isSdkSeemsValid
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

  return PythonInterpreterPresentation(
    name = customName ?: name,
    suffix = secondary,
    description = homePath ?: "[invalid]",
    modifier = modifier,
    icon = icon(this)
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
