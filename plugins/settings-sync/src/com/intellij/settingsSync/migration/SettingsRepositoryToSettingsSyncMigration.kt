package com.intellij.settingsSync.migration

import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigBackup
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.FileUtil
import com.intellij.settingsSync.*
import com.intellij.settingsSync.plugins.PluginManagerProxy
import com.intellij.settingsSync.statistics.SettingsSyncEventsStatistics
import com.intellij.settingsSync.statistics.SettingsSyncEventsStatistics.SettingsRepositoryMigrationNotificationAction.INSTALL_SETTINGS_REPOSITORY
import com.intellij.settingsSync.statistics.SettingsSyncEventsStatistics.SettingsRepositoryMigrationNotificationAction.USE_NEW_SETTINGS_SYNC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import kotlin.io.path.*

private const val SETTINGS_REPOSITORY_ID = "org.jetbrains.settingsRepository"

private val LOG = logger<SettingsRepositoryToSettingsSyncMigration>()

private val SPECIAL_FILES = listOf(".git", "config.json")

private val OS_PREFIXES = listOf(
  "_mac" to "mac",
  "_windows" to "windows",
  "_linux" to "linux",
  "_freebsd" to "freebsd",
  "_unix" to "unix"
)

internal class SettingsRepositoryToSettingsSyncMigration {
  fun getLocalDataIfAvailable(appConfigDir: Path): SettingsSnapshot? {
    return processLocalData(appConfigDir) { path ->
      readLocalData(path)
    }
  }

  fun isLocalDataAvailable(appConfigDir: Path): Boolean {
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
            if (!file.isRegularFile()) return FileVisitResult.CONTINUE

            val relative = settingsRepositoryConfigPath.relativize(file).invariantSeparatorsPathString
            val relativeWithFixedPrefix = if (prefix != null) relative.replaceFirst(prefix.first, prefix.second) else relative

            val fileSpec =
              if (prefix != null || file == topLevelFile) { // all settings under options/ are either on top level or under the per-os folder
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
    return SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo()), fileStates,
                            plugins = null, emptyMap(), emptySet())
  }
}

private fun showNotificationAboutUnbundling(coroutineScope: CoroutineScope, executorService: ScheduledExecutorService) {
  val installOldPluginAction = NotificationAction.createSimpleExpiring(
    @Suppress("DialogTitleCapitalization") // name of plugin is capitalized
    SettingsSyncBundle.message("settings.repository.unbundled.notification.action.install.settings.repository")) {
    coroutineScope.launch {
      serviceAsync<PluginManagerProxy>().createInstaller(notifyErrors = true).installPlugins(listOf(PluginId.getId(SETTINGS_REPOSITORY_ID)))
    }
    SettingsSyncEventsStatistics.SETTINGS_REPOSITORY_NOTIFICATION_ACTION.log(INSTALL_SETTINGS_REPOSITORY)
  }
  val useNewSettingsSyncAction = NotificationAction.createSimpleExpiring(
    @Suppress("DialogTitleCapitalization") // name of plugin is capitalized
    SettingsSyncBundle.message("settings.repository.unbundled.notification.action.use.new.settings.sync")) {
    SettingsSyncSettings.getInstance().syncEnabled = true
    executorService.submit {
      SettingsSyncMain.getInstance().controls.bridge.initialize(SettingsSyncBridge.InitMode.PushToServer)
    }
    SettingsSyncEventsStatistics.SETTINGS_REPOSITORY_NOTIFICATION_ACTION.log(USE_NEW_SETTINGS_SYNC)
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

internal suspend fun migrateIfNeeded(coroutineScope: CoroutineScope, executorService: ScheduledExecutorService) {
  if (PluginManager.isPluginInstalled(PluginId.getId(SETTINGS_REPOSITORY_ID))) {
    return
  }

  val settingsRepositoryMigration = SettingsRepositoryToSettingsSyncMigration()
  if (!settingsRepositoryMigration.isLocalDataAvailable(PathManager.getConfigDir())) {
    return
  }

  LOG.info("Migrating from the Settings Repository")
  val snapshot = settingsRepositoryMigration.getLocalDataIfAvailable(PathManager.getConfigDir()) ?: return
    backupCurrentConfig()
    TemplateSettings.getInstance() // Required for live templates to be migrated correctly, see IDEA-303831

    SettingsSyncIdeMediatorImpl(ApplicationManager.getApplication().stateStore as ComponentStoreImpl,
                                PathManager.getConfigDir()) { false }.applyToIde(snapshot, null)
    showNotificationAboutUnbundling(coroutineScope, executorService)
    SettingsSyncEventsStatistics.MIGRATED_FROM_SETTINGS_REPOSITORY.log()
}

private fun backupCurrentConfig() {
  val configDir = PathManager.getConfigDir()
  val tempBackupDir = Files.createTempDirectory(configDir.fileName.toString() + "-backup-" + UUID.randomUUID())
  LOG.info("Backup config from ${configDir} to ${tempBackupDir}")
  FileUtil.copyDir(configDir.toFile(), tempBackupDir.toFile())
  ConfigBackup(PathManager.getConfigDir()).moveToBackup(tempBackupDir)
}