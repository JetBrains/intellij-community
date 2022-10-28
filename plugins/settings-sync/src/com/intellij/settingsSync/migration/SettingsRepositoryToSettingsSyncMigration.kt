package com.intellij.settingsSync.migration

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.*
import com.intellij.settingsSync.config.EDITOR_FONT_SUBCATEGORY_ID
import com.intellij.util.io.isFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlin.io.path.*

internal class SettingsRepositoryToSettingsSyncMigration : SettingsSyncMigration {
  override fun getLocalDataIfAvailable(appConfigDir: Path): SettingsSnapshot? {
    return processLocalData(appConfigDir) { path ->
      readLocalData(path)
    }
  }

  override fun isLocalDataAvailable(appConfigDir: Path): Boolean {
    if (PluginManager.isPluginInstalled(PluginId.getId("org.jetbrains.settingsRepository"))) {
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