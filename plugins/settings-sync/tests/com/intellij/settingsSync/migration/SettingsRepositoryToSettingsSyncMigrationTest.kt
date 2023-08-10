package com.intellij.settingsSync.migration

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.openapi.util.SystemInfo
import com.intellij.settingsSync.SettingsSnapshot
import com.intellij.settingsSync.assertSettingsSnapshot
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.createDirectories
import com.intellij.util.io.write
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.div

class SettingsRepositoryToSettingsSyncMigrationTest {
  @JvmField
  @Rule
  val memoryFs = InMemoryFsRule()
  private val fs get() = memoryFs.fs

  @JvmField
  @Rule
  val applicationRule = ApplicationRule()

  private val rootConfig: Path get() = fs.getPath("/AppConfig")

  private val _repoConfig = lazy {
    (rootConfig / "settingsRepository" / "repository").apply {
      val git = (this / ".git").createDirectories()
      (git / "objects" / "2a" / "546c40284dcb6b33d6279a7345c56ef32cd2fc").write("Blob")
    }
  }
  private val repoConfig: Path get() = _repoConfig.value

  private val os : String get() = when {
    SystemInfo.isMac -> "_mac"
    SystemInfo.isWindows -> "_windows"
    SystemInfo.isLinux -> "_linux"
    SystemInfo.isFreeBSD -> "_freebsd"
    SystemInfo.isUnix -> "_unix"
    else -> "_unknown"
  }

  @Test
  fun `test migration from local storage`() {
    (repoConfig / "laf.xml").write("LaF")
    (repoConfig / "colors" / "myscheme.icls").write("MyColorScheme")
    (repoConfig / os / "keymap.xml").write("Keymap")
    (repoConfig / "keymaps" / "mykeymap.xml").write("MyKeyMap")
    (repoConfig / "fileTemplates" / "code" / "mytemplate.kt").write("My Code Template")

    val snapshot = migrate()

    val ros = getPerOsSettingsStorageFolderName()
    snapshot.assertSettingsSnapshot {
      fileState("options/laf.xml", "LaF")
      fileState("colors/myscheme.icls", "MyColorScheme")
      fileState("keymaps/mykeymap.xml", "MyKeyMap")
      fileState("options/$ros/keymap.xml", "Keymap")
      fileState("fileTemplates/code/mytemplate.kt", "My Code Template")
    }
  }

  private fun migrate(): SettingsSnapshot {
    val migration = SettingsRepositoryToSettingsSyncMigration()
    assertTrue(migration.isLocalDataAvailable(rootConfig))
    val snapshot = migration.getLocalDataIfAvailable(rootConfig)
    assertNotNull(snapshot)
    return snapshot!!
  }
}