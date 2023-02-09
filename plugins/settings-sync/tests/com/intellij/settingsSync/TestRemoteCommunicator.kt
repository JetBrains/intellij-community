package com.intellij.settingsSync

import org.junit.Assert
import java.util.concurrent.CountDownLatch

internal abstract class TestRemoteCommunicator : SettingsSyncRemoteCommunicator {

  private lateinit var pushedLatch: CountDownLatch
  private lateinit var pushedSnapshot: SettingsSnapshot

  abstract fun prepareFileOnServer(snapshot: SettingsSnapshot)

  abstract fun getVersionOnServer(): SettingsSnapshot?

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

  abstract fun deleteAllFiles()
}