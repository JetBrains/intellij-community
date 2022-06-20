package com.intellij.settingsSync

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant
import kotlin.random.Random

@RunWith(JUnit4::class)
class SettingsSnapshotZipSerializerTest {

  @Test
  fun `serialize snapshot to zip`() {
    val date = Instant.ofEpochMilli(Random.nextLong())
    val snapshot = settingsSnapshot(date) {
      fileState("options/laf.xml", "Laf")
      fileState("colors/my.icls", "Color Scheme")
      fileState("file.xml", "File")
    }
    val zip = SettingsSnapshotZipSerializer.serializeToZip(snapshot)

    val actualSnapshot = SettingsSnapshotZipSerializer.extractFromZip(zip)
    assertEquals(date, actualSnapshot.metaInfo.dateCreated)
    assertEquals(snapshot.fileStates, actualSnapshot.fileStates)
  }
}