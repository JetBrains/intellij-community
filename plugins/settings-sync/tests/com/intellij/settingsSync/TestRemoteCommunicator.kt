package com.intellij.settingsSync

import com.intellij.util.resettableLazy
import org.junit.Assert
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.io.path.inputStream

internal open class TestRemoteCommunicator(customServerUrl: String = "http://localhost:7777/cloudconfig")
  : CloudConfigServerCommunicator(customServerUrl) {

  override val _userId = resettableLazy { "it's set to avoid UninitializedPropertyAccessException" }
  override val _idToken = resettableLazy { "it's set to avoid UninitializedPropertyAccessException" }

  private lateinit var pushedLatch: CountDownLatch
  private lateinit var pushedSnapshot: SettingsSnapshot

  open fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    val zip = SettingsSnapshotZipSerializer.serializeToZip(snapshot)
    sendSnapshotFile(zip.inputStream(), null, true, clientVersionContext, client)
  }

  open fun deleteAllFiles() {
    client.delete("*")
  }

  open fun getVersionOnServer(): SettingsSnapshot? =
    when (val updateResult = receiveUpdates()) {
      is UpdateResult.Success -> updateResult.settingsSnapshot
      UpdateResult.FileDeletedFromServer -> snapshotForDeletion()
      UpdateResult.NoFileOnServer -> null
      is UpdateResult.Error -> throw AssertionError(updateResult.message)
    }

  fun awaitForPush(testExecution: () -> Unit): SettingsSnapshot {
    pushedLatch = CountDownLatch(1)
    testExecution()
    Assert.assertTrue("Didn't await until changes are pushed", pushedLatch.wait())
    return pushedSnapshot
  }

  protected fun settingsPushed(snapshot: SettingsSnapshot) {
    if (::pushedLatch.isInitialized) {
      pushedSnapshot = snapshot
      pushedLatch.countDown()
    }
  }

  private fun snapshotForDeletion() =
    SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true), emptySet(), null, emptyMap(), emptySet())

  override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
    val result = super.push(snapshot, force, expectedServerVersionId)
    settingsPushed(snapshot)
    return result
  }

}
