// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.validator.rules.impl.AllowedItemsResourceWeakRefStorage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.PathUtil
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.system.OS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics.shellVersionField
import java.util.Locale

@ApiStatus.Internal
object TerminalShellInfoStatistics {
  private val knownPromptThemes: List<String> = getKnownPromptThemes()

  val shellVersionField: StringEventField = EventFields.StringValidatedByRegexpReference("shell_version", "version")
  val promptThemeField: StringEventField = EventFields.String("prompt_theme", knownPromptThemes)

  val isOhMyPoshField: BooleanEventField = EventFields.Boolean("is_oh_my_posh", "Zsh, Bash, Powershell")
  val isStarshipField: BooleanEventField = EventFields.Boolean("is_starship", "Zsh, Bash, Powershell")

  val isOhMyZshField: BooleanEventField = EventFields.Boolean("is_oh_my_zsh", "Zsh only")
  val isP10KField: BooleanEventField = EventFields.Boolean("is_p10k", "Zsh only")
  val isSpaceshipField: BooleanEventField = EventFields.Boolean("is_spaceship", "Zsh only")
  val isPreztoField: BooleanEventField = EventFields.Boolean("is_prezto", "Zsh only")

  val isOhMyBashField: BooleanEventField = EventFields.Boolean("is_oh_my_bash", "Bash only")
  val isBashItField: BooleanEventField = EventFields.Boolean("is_bash_it", "Bash only")

  const val UNSPECIFIED_SHELL_NAME: String = "unspecified"
  const val OTHER_SHELL_NAME: String = "other"

  val KNOWN_SHELLS: Set<String> = setOf(UNSPECIFIED_SHELL_NAME,
                                        OTHER_SHELL_NAME,
                                        "activate",
                                        "anaconda3",
                                        "ash",
                                        "bash",
                                        "bbsh",
                                        "cexec",
                                        "cmd",
                                        "cmder",
                                        "cmder_shell",
                                        "csh",
                                        "cygwin",
                                        "dash",
                                        "es",
                                        "eshell",
                                        "fish",
                                        "fsh",
                                        "git",
                                        "git-bash",
                                        "git-cmd",
                                        "hamilton",
                                        "init",
                                        "ion",
                                        "ksh",
                                        "miniconda3",
                                        "mksh",
                                        "msys2_shell",
                                        "nushell",
                                        "powershell",
                                        "pwsh",
                                        "rc",
                                        "scsh",
                                        "sh",
                                        "tcsh",
                                        "ubuntu",
                                        "ubuntu1804",
                                        "wsl",
                                        "xonsh",
                                        "zsh")

  private val KNOWN_EXTENSIONS = setOf("exe", "bat", "cmd")

  private val JSON: Json = Json { ignoreUnknownKeys = true }

  fun getLoggableShellInfo(rawShellInfo: String): LoggableShellInfo? {
    val shellInfo = parseShellInfo(rawShellInfo) ?: return null
    val shellVersion = adjustVersionString(shellInfo.shellVersion).takeIf { it.isNotEmpty() }

    val ohMyZshTheme = getThemeName(shellInfo.ohMyZshTheme)
    val ohMyPoshTheme = getThemeName(shellInfo.ohMyPoshTheme)?.removeSuffix(".omp.json")
    val preztoTheme = getThemeName(shellInfo.preztoTheme)
    val ohMyBashTheme = getThemeName(shellInfo.ohMyBashTheme)
    val bashItTheme = getThemeName(shellInfo.bashItTheme)
    // It is hard to guess the real theme if themes from several plugins are set. So, it is more heuristic.
    val theme = if (shellInfo.isP10K) {
      ohMyBashTheme ?: ohMyZshTheme ?: ohMyPoshTheme ?: bashItTheme ?: preztoTheme
    }
    else ohMyBashTheme ?: ohMyPoshTheme ?: bashItTheme ?: ohMyZshTheme ?: preztoTheme

    return LoggableShellInfo(
      shellVersion = shellVersion,
      promptTheme = theme,
      isOhMyZsh = shellInfo.isOhMyZsh,
      isOhMyPosh = shellInfo.ohMyPoshTheme.isNotEmpty(),
      isP10K = shellInfo.isP10K,
      isStarship = shellInfo.isStarship,
      isSpaceship = shellInfo.isSpaceship,
      isPrezto = shellInfo.isPrezto,
      isOhMyBash = shellInfo.isOhMyBash,
      isBashIt = shellInfo.isBashIt,
    )
  }

  private fun parseShellInfo(rawShellInfo: String): ShellInfo? {
    return try {
      JSON.decodeFromString<ShellInfo>(rawShellInfo)
    }
    catch (t: Throwable) {
      thisLogger().error("Failed to parse shell info: $rawShellInfo")
      null
    }
  }

  private fun getThemeName(themePath: String): String? {
    return PathUtil.getFileName(themePath).takeIf { it.isNotEmpty() }
  }

  /**
   * Truncates the provided [version] to get the string, that contains only digits and dots.
   * For example, the Bash version can look like this: `5.2.15(1)-release`.
   * Better to leave only meaningful part `5.2.15` in this case to be able to pass validation of [shellVersionField].
   */
  private fun adjustVersionString(version: String): String {
    val meaningfulInfoEndOffset = version.indexOfFirst { !it.isDigit() && it != '.' }.takeIf { it != -1 } ?: version.length
    return version.substring(0, meaningfulInfoEndOffset)
  }

  private fun getKnownPromptThemes(): List<String> {
    val ohMyZshThemes = getKnownThemes("known-ohmyzsh-themes.txt")
    val ohMyPoshThemes = getKnownThemes("known-ohmyposh-themes.txt")
    val preztoThemes = getKnownThemes("known-prezto-themes.txt")
    val ohMyBashThemes = getKnownThemes("known-ohmybash-themes.txt")
    val bashItThemes = getKnownThemes("known-bashit-themes.txt")
    return sequence {
      yieldAll(ohMyZshThemes)
      yieldAll(ohMyPoshThemes)
      yieldAll(preztoThemes)
      yieldAll(ohMyBashThemes)
      yieldAll(bashItThemes)
      yield("default")  // our own name for the default OhMyPosh theme
      yield("powerlevel10k")
      yield("powerlevel9k")
      yield("spaceship")
    }.distinct().toList()
  }

  private fun getKnownThemes(path: String): Collection<String> {
    return AllowedItemsResourceWeakRefStorage(TerminalShellInfoStatistics::class.java, path).items
  }

  /**
   * @param shellCommand shell command line to extract the shell name from
   */
  fun getShellNameForStat(shellCommand: String?): String {
    if (shellCommand == null) return UNSPECIFIED_SHELL_NAME
    val command = ParametersListUtil.parse(shellCommand, false, OS.CURRENT != OS.Windows)
    val shellPath = command.firstOrNull() ?: return UNSPECIFIED_SHELL_NAME
    val shellName = PathUtil.getFileName(shellPath).lowercase(Locale.ENGLISH)
    val trimmedName = trimKnownExt(shellName)
    return if (KNOWN_SHELLS.contains(trimmedName)) trimmedName else OTHER_SHELL_NAME
  }

  private fun trimKnownExt(name: String): String {
    val ext = PathUtil.getFileExtension(name)
    return if (ext != null && KNOWN_EXTENSIONS.contains(ext)) name.substring(0, name.length - ext.length - 1) else name
  }

  @Serializable
  private data class ShellInfo(
    val shellVersion: String = "",
    val isOhMyZsh: Boolean = false,
    val isP10K: Boolean = false,
    val isStarship: Boolean = false,
    val isSpaceship: Boolean = false,
    val isPrezto: Boolean = false,
    val isOhMyBash: Boolean = false,
    val isBashIt: Boolean = false,
    val ohMyZshTheme: String = "",
    val ohMyPoshTheme: String = "",
    val preztoTheme: String = "",
    val ohMyBashTheme: String = "",
    val bashItTheme: String = ""
  )

  data class LoggableShellInfo(
    val shellVersion: String?,
    val promptTheme: String?,
    val isOhMyZsh: Boolean,
    val isOhMyPosh: Boolean,
    val isP10K: Boolean,
    val isStarship: Boolean,
    val isSpaceship: Boolean,
    val isPrezto: Boolean,
    val isOhMyBash: Boolean,
    val isBashIt: Boolean,
  )
}