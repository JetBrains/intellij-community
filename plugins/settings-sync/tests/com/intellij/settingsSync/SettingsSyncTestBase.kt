package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.io.createDirectories
import com.intellij.util.io.write
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText

internal val TIMEOUT_UNIT = TimeUnit.SECONDS

@TestApplication
internal abstract class SettingsSyncTestBase {

  companion object {
    val LOG = logger<SettingsSyncTestBase>()
  }

  protected lateinit var application: ApplicationImpl
  protected lateinit var configDir: Path
  protected lateinit var remoteCommunicator: TestRemoteCommunicator
  protected lateinit var updateChecker: SettingsSyncUpdateChecker
  protected lateinit var bridge: SettingsSyncBridge

  @TestDisposable
  protected lateinit var disposable: Disposable
  protected val settingsSyncStorage: Path get() = configDir.resolve("settingsSync")

  @BeforeEach
  fun setup(@TempDir mainDir: Path) {
    application = ApplicationManager.getApplication() as ApplicationImpl
    configDir = mainDir.resolve("rootconfig").createDirectories()

    SettingsSyncLocalSettings.getInstance().state.reset()
    SettingsSyncSettings.getInstance().state = SettingsSyncSettings.State()

    remoteCommunicator = if (isTestingAgainstRealCloudServer()) {
      TestRemoteCommunicator()
    }
    else {
      MockRemoteCommunicator()
    }

    val serverState = remoteCommunicator.checkServerState()
    if (serverState != ServerState.FileNotExists) {
      LOG.warn("Server state: $serverState")
      remoteCommunicator.deleteAllFiles()
    }
  }

  @AfterEach
  fun cleanup() {
    if (::bridge.isInitialized) {
      bridge.waitForAllExecuted()
    }

    remoteCommunicator.deleteAllFiles()
  }

  protected fun writeToConfig(build: SettingsSnapshotBuilder.() -> Unit) {
    val builder = SettingsSnapshotBuilder()
    builder.build()
    for (file in builder.fileStates) {
      file as FileState.Modified
      configDir.resolve(file.file).write(file.content)
    }
  }

  protected fun assertFileWithContent(expectedContent: String, file: Path) {
    assertTrue(file.exists(), "File $file does not exist")
    assertEquals(expectedContent, file.readText(), "File $file has unexpected content")
  }

  protected fun assertServerSnapshot(build: SettingsSnapshotBuilder.() -> Unit) {
    val pushedSnapshot = remoteCommunicator.getVersionOnServer()
    assertNotNull(pushedSnapshot, "Nothing has been pushed")
    pushedSnapshot!!.assertSettingsSnapshot {
      build()
    }
  }

  protected fun executeAndWaitUntilPushed(testExecution: () -> Unit): SettingsSnapshot {
    return remoteCommunicator.awaitForPush(testExecution)
  }
}

internal fun SettingsSyncBridge.waitForAllExecuted() {
  this.waitForAllExecuted(getDefaultTimeoutInSeconds(), TIMEOUT_UNIT)
}

internal fun CountDownLatch.wait(): Boolean {
  return this.await(getDefaultTimeoutInSeconds(), TIMEOUT_UNIT)
}

private fun isTestingAgainstRealCloudServer() = System.getenv("SETTINGS_SYNC_TEST_CLOUD") == "real"

private fun getDefaultTimeoutInSeconds(): Long = 10
