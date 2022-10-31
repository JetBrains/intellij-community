package com.intellij.settingsSync.migration

import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.*
import com.intellij.settingsSync.config.EDITOR_FONT_SUBCATEGORY_ID
import com.intellij.settingsSync.plugins.PluginManagerProxy
import com.intellij.util.io.isFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlin.io.path.*

private const val SETTINGS_REPOSITORY_ID = "org.jetbrains.settingsRepository"

internal class SettingsRepositoryToSettingsSyncMigration : SettingsSyncMigration {
  override fun getLocalDataIfAvailable(appConfigDir: Path): SettingsSnapshot? {
    return processLocalData(appConfigDir) { path ->
      readLocalData(path)
    }
  }

  override fun isLocalDataAvailable(appConfigDir: Path): Boolean {
    if (PluginManager.isPluginInstalled(PluginId.getId(SETTINGS_REPOSITORY_ID))) {
      return false
    }
    return null != processLocalData(appConfigDir) { it }
  }

  private fun <T> processLocalData(appConfigDir: Path, processor: (Path) -> T?): T? {
    try {
      val configPath = appConfigDir / "settingsRepository" / "repository"
      val repositoryPath = configPath / ".git"
      if (repositoryPath.exists()) {
        return processor(configPath)
      }
      else {
        LOG.info("No data from settingsRepository in 'settingsRepository' folder => no migration needed")
      }
    }
    catch (e: Exception) {
      LOG.error("Could not read data from settings repository => no migration ", e)
    }
    return null
  }

  private fun readLocalData(settingsRepositoryConfigPath: Path): SettingsSnapshot {
    val fileStates = mutableSetOf<FileState>()
    Files.list(settingsRepositoryConfigPath).forEach { topLevelFile ->
      val prefix = OS_PREFIXES.find { topLevelFile.name.startsWith(it.first) }
      if (!SPECIAL_FILES.contains(topLevelFile.name)) {
        Files.walkFileTree(topLevelFile, object : SimpleFileVisitor<Path>() {
          override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
            if (!file.isFile()) return FileVisitResult.CONTINUE

            val relative = settingsRepositoryConfigPath.relativize(file).invariantSeparatorsPathString
            val relativeWithFixedPrefix = if (prefix != null) relative.replaceFirst(prefix.first, prefix.second) else relative

            val fileSpec =
              if (prefix != null || file == topLevelFile) { // all settings under options/ are either on top level, or under the per-os folder
                "options/$relativeWithFixedPrefix"
              }
              else relativeWithFixedPrefix

            val content = file.readBytes()
            fileStates += FileState.Modified(fileSpec, content)
            return FileVisitResult.CONTINUE
          }
        })
      }
    }
    return SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo()), fileStates, plugins = null)
  }

  override fun migrateCategoriesSyncStatus(appConfigDir: Path, syncSettings: SettingsSyncSettings) {
    syncSettings.setSubcategoryEnabled(SettingsCategory.UI, EDITOR_FONT_SUBCATEGORY_ID, false)
  }

  override fun shouldEnableNewSync(): Boolean {
    return false
  }

  override fun executeAfterApplying() {
    if (!PluginManager.isPluginInstalled(PluginId.getId(SETTINGS_REPOSITORY_ID))) {
      showNotificationAboutUnbundling()
    }
  }

  private fun showNotificationAboutUnbundling() {
    val installOldPluginAction = NotificationAction.createSimpleExpiring(
      @Suppress("DialogTitleCapitalization") // name of plugin is capitalized
      SettingsSyncBundle.message("settings.repository.unbundled.notification.action.install.settings.repository")) {
      PluginManagerProxy.getInstance().createInstaller(notifyErrors = true).installPlugins(listOf(PluginId.getId(SETTINGS_REPOSITORY_ID)))
    }
    val useNewSettingsSyncAction = NotificationAction.createSimpleExpiring(
      @Suppress("DialogTitleCapitalization") // name of plugin is capitalized
      SettingsSyncBundle.message("settings.repository.unbundled.notification.action.use.new.settings.sync")) {
      SettingsSyncSettings.getInstance().syncEnabled = true
    }
    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
      .createNotification(
        title = SettingsSyncBundle.message("settings.repository.unbundled.notification.title"),
        content = SettingsSyncBundle.message("settings.repository.unbundled.notification.description"),
        type = NotificationType.INFORMATION)
      .addAction(installOldPluginAction)
      .addAction(useNewSettingsSyncAction)
      .notify(null)
  }

  internal companion object {
    val LOG = logger<SettingsRepositoryToSettingsSyncMigration>()

    val SPECIAL_FILES = listOf(".git", "config.json")

    private val OS_PREFIXES = listOf(
      "_mac" to "mac",
      "_windows" to "windows",
      "_linux" to "linux",
      "_freebsd" to "freebsd",
      "_unix" to "unix"
    )
  }
}