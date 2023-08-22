package com.intellij.settingsSync

import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.util.BuildNumber
import com.intellij.settingsSync.SettingsSnapshot.AppInfo
import com.intellij.settingsSync.SettingsSnapshot.MetaInfo
import com.intellij.testFramework.registerExtension
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant
import java.util.*
import kotlin.random.Random

@RunWith(JUnit4::class)
internal class SettingsSnapshotZipSerializerTest : SettingsSyncTestBase() {

  @Before
  internal fun initFields() {
    val settingsProvider = SettingsProviderTest.TestSettingsProvider()
    application.registerExtension(SettingsProvider.SETTINGS_PROVIDER_EP, settingsProvider, disposable)
  }

  @Test
  fun `serialize snapshot to zip`() {
    val date = Instant.ofEpochMilli(Random.nextLong())
    val snapshot = settingsSnapshot(
      MetaInfo(date, AppInfo(UUID.randomUUID(), BuildNumber.fromString("IU-231.1"), "john", "home", "/Users/john/ideaconfig/"))) {
      fileState("options/laf.xml", "Laf")
      fileState("colors/my.icls", "Color Scheme")
      fileState("file.xml", "File")
      plugin("com.jetbrains.CyanTheme", false, SettingsCategory.UI, setOf("com.intellij.modules.lang"))
      additionalFile("newformat.json", "New format")
      provided("test", SettingsProviderTest.TestState("just value"))
    }
    val zip = SettingsSnapshotZipSerializer.serializeToZip(snapshot)

    val actualSnapshot = SettingsSnapshotZipSerializer.extractFromZip(zip)
    assertSettingsSnapshotsEqual(snapshot, actualSnapshot)
  }
}