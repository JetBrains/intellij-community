package com.intellij.settingsSync

import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.SettingsSnapshot.AppInfo
import com.intellij.settingsSync.SettingsSnapshot.MetaInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant
import java.util.*
import kotlin.random.Random

@RunWith(JUnit4::class)
class SettingsSnapshotZipSerializerTest {

  @Test
  fun `serialize snapshot to zip`() {
    val date = Instant.ofEpochMilli(Random.nextLong())
    val snapshot = settingsSnapshot(MetaInfo(date, AppInfo(UUID.randomUUID(), "", "", ""))) {
      fileState("options/laf.xml", "Laf")
      fileState("colors/my.icls", "Color Scheme")
      fileState("file.xml", "File")
      plugin("com.jetbrains.CyanTheme", false, SettingsCategory.UI, setOf("com.intellij.modules.lang"))
    }
    val zip = SettingsSnapshotZipSerializer.serializeToZip(snapshot)

    val actualSnapshot = SettingsSnapshotZipSerializer.extractFromZip(zip)
    assertEquals(date, actualSnapshot.metaInfo.dateCreated)
    assertEquals(snapshot.plugins, actualSnapshot.plugins)
    assertEquals(snapshot.fileStates, actualSnapshot.fileStates)
  }
}