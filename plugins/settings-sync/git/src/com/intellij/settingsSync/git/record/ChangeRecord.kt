package com.intellij.settingsSync.git.record

import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.settingsSync.GitSettingsLog
import com.intellij.settingsSync.SettingsSyncBundle
import com.intellij.settingsSync.getCategory
import com.intellij.vcs.log.VcsFullCommitDetails
import java.text.DateFormat
import java.util.*

internal sealed class HistoryRecord(private val commitDetails: VcsFullCommitDetails, val isFirstCommit: Boolean, isLastCommit: Boolean) {
  val id = commitDetails.id
  val position: RecordPosition
  val time: String
    get() = getTime(commitDetails.commitTime)

  init {
    position = if (isFirstCommit && isLastCommit) {
      RecordPosition.SINGLE
    } else if (isFirstCommit) {
      RecordPosition.BOTTOM
    } else if (isLastCommit) {
      RecordPosition.TOP
    } else {
      RecordPosition.MIDDLE
    }
  }

  protected fun getTime(commitTime: Long): String {
    fun isDateOffsetBy(date: Date, offset: Int): Boolean {
      val now = Calendar.getInstance()
      now.add(Calendar.DAY_OF_YEAR, offset)
      val givenTime = Calendar.getInstance()
      givenTime.time = date

      return now.get(Calendar.YEAR) == givenTime.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == givenTime.get(Calendar.DAY_OF_YEAR)
    }

    fun isToday(date: Date): Boolean {
      return isDateOffsetBy(date, 0)
    }

    fun isYesterday(date: Date): Boolean {
      return isDateOffsetBy(date, -1)
    }

    val commitTimeDate = Date(commitTime)

    val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    val timePart = timeFormatter.format(commitTimeDate)

    return if (isToday(commitTimeDate)) {
      SettingsSyncBundle.message("ui.toolwindow.time.today", timePart)
    } else if (isYesterday(commitTimeDate)) {
      SettingsSyncBundle.message("ui.toolwindow.time.yesterday", timePart)
    } else {
      val dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
      val datePart = dateFormatter.format(commitTimeDate)
      SettingsSyncBundle.message("ui.toolwindow.time.date", datePart, timePart)
    }
  }

  enum class RecordPosition {
    SINGLE,
    TOP,
    MIDDLE,
    BOTTOM,
  }
}

internal class ChangeRecord(private val commitDetails: VcsFullCommitDetails, isFirstCommit: Boolean, isLastCommit: Boolean, private val commits: List<VcsFullCommitDetails>): HistoryRecord(commitDetails, isFirstCommit, isLastCommit) {
  companion object {
    private val logger = logger<ChangeRecord>()
  }

  val changes: List<Change> = commitDetails.changes.toList()
  val origin: ChangeOrigin = getChangeOrigin()
  val title: String = getChangedCategories()
  val build: String = parseBuildFromCommitDetails()
  val host: String = parseHostFromCommitDetails()
  val restored: String? = parseRestoredFromCommitDetails()
  val os: OperatingSystem? = parseOSFromCommitDetails()

  private fun getChangeOrigin(): ChangeOrigin {
    val idInCommitMessage = commitDetails.fullMessage.lines().singleOrNull { it.startsWith( "id:") }
                            ?: return ChangeOrigin.Local

    return when {
      idInCommitMessage.contains("[this]") -> ChangeOrigin.Local
      idInCommitMessage.contains("[other]") -> ChangeOrigin.Remote
      else -> {
        ChangeOrigin.Local
      }
    }
  }

  private fun getChangedCategories(): String {
    fun getCategoryOrder(category: SettingsCategory): Int {
      return when (category) {
        SettingsCategory.SYSTEM -> 1
        SettingsCategory.CODE -> 2
        SettingsCategory.PLUGINS -> 3
        SettingsCategory.KEYMAP -> 4
        SettingsCategory.TOOLS -> 5
        SettingsCategory.UI -> 6
        SettingsCategory.OTHER -> 7
      }
    }

    if (isFirstCommit) return "Initial"

    fun getChangeCategory(change: Change): SettingsCategory {
      val fileName = change.virtualFile?.name ?: return SettingsCategory.OTHER
      if (fileName == GitSettingsLog.PLUGINS_FILE) return SettingsCategory.PLUGINS

      //workaround empty category
      return getCategory(fileName) ?: SettingsCategory.OTHER
    }

    val changesCategories = changes.map { getChangeCategory(it) }.distinct().sortedBy { getCategoryOrder(it) }.map { toString(it) }
    return changesCategories.joinToString()
  }

  private fun toString(category: SettingsCategory): String {
    return when (category) {
      SettingsCategory.SYSTEM -> SettingsSyncBundle.message("ui.toolwindow.change.category.system")
      SettingsCategory.CODE -> SettingsSyncBundle.message("ui.toolwindow.change.category.code")
      SettingsCategory.PLUGINS -> SettingsSyncBundle.message("ui.toolwindow.change.category.plugins")
      SettingsCategory.KEYMAP -> SettingsSyncBundle.message("ui.toolwindow.change.category.keymap")
      SettingsCategory.TOOLS -> SettingsSyncBundle.message("ui.toolwindow.change.category.tools")
      SettingsCategory.UI -> SettingsSyncBundle.message("ui.toolwindow.change.category.ui")
      SettingsCategory.OTHER -> SettingsSyncBundle.message("ui.toolwindow.change.category.other")
    }
  }

  private fun parseBuildFromCommitDetails(): String {
    return commitDetails.fullMessage
             .lines()
             .singleOrNull { it.startsWith("build:") }
             ?.removePrefix("build:")
             ?.trim()
           ?: ""
  }

  private fun parseHostFromCommitDetails(): String {
    return commitDetails.fullMessage
             .lines()
             .singleOrNull { it.startsWith("host:") }
             ?.removePrefix("host:")
             ?.trim()
           ?: ""
  }

  private fun parseRestoredFromCommitDetails(): String? {
    val hash = commitDetails.fullMessage
             .lines()
             .singleOrNull { it.startsWith("restores:") }
             ?.removePrefix("restores:")
             ?.trim() ?: return null

    val commitForHash = commits.firstOrNull { it.id.asString() == hash }
    return if (commitForHash == null) {
      // todo log (once)
      SettingsSyncBundle.message("ui.toolwindow.restored.to.hash.text", hash)
    } else {
      SettingsSyncBundle.message("ui.toolwindow.restored.from.date.text", getTime(commitForHash.timestamp))
    }
  }

  private fun parseOSFromCommitDetails(): OperatingSystem? {
    val osString = commitDetails.fullMessage
                 .lines()
                 .singleOrNull { it.startsWith("os:") }
                 ?.removePrefix("os:")
                 ?.trim()
                 ?.lowercase() ?: return null
    return when {
      osString.contains("linux") -> OperatingSystem.LINUX
      osString.contains("macos") -> OperatingSystem.MAC
      osString.contains("windows") -> OperatingSystem.WINDOWS
      else -> null
    }
  }

  enum class OperatingSystem {
    LINUX,
    MAC,
    WINDOWS,
  }

  sealed class ChangeOrigin {
    data object Local: ChangeOrigin()
    data object Remote: ChangeOrigin()
  }
}

internal class MergeRecord(private val commitDetails: VcsFullCommitDetails, isFirstCommit: Boolean, isLastCommit: Boolean): HistoryRecord(commitDetails, isFirstCommit, isLastCommit)
