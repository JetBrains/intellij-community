package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.io.createDirectories
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import com.intellij.util.io.write
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal val TIMEOUT_UNIT = TimeUnit.SECONDS

internal abstract class SettingsSyncTestBase {

  companion object {
    val LOG = logger<SettingsSyncTestBase>()
  }

  private val appRule = ApplicationRule()
  private val tempDirManager = TemporaryDirectory()
  private val disposableRule = DisposableRule()
  @Rule @JvmField val ruleChain: RuleChain = RuleChain.outerRule(tempDirManager).around(appRule).around(disposableRule)

  @Rule @JvmField val logger = TestLoggerFactory.createTestWatcher()

  protected lateinit var application: ApplicationImpl
  protected lateinit var configDir: Path
  protected lateinit var remoteCommunicator: TestRemoteCommunicator
  protected lateinit var updateChecker: SettingsSyncUpdateChecker
  protected lateinit var bridge: SettingsSyncBridge

  protected val disposable: Disposable get() = disposableRule.disposable
  protected val settingsSyncStorage: Path get() = configDir.resolve("settingsSync")

  @Before
  fun setup() {
    application = ApplicationManager.getApplication() as ApplicationImpl
    val mainDir = tempDirManager.createDir()
    configDir = mainDir.resolve("rootconfig").createDirectories()

    SettingsSyncLocalSettings.getInstance().state.reset()
    SettingsSyncSettings.getInstance().state.reset()

    remoteCommunicator = if (isTestingAgainstRealCloudServer()) {
      System.setProperty(CloudConfigServerCommunicator.URL_PROPERTY, CloudConfigServerCommunicator.DEFAULT_PRODUCTION_URL)
      TestCloudConfigRemoteCommunicator()
    }
    else {
      MockRemoteCommunicator()
    }

    val serverState = remoteCommunicator.checkServerState()
    if (serverState != ServerState.FileNotExists) {
      LOG.warn("Server state: $serverState")
      remoteCommunicator.delete()
    }
  }

  @After
  fun cleanup() {
    if (::bridge.isInitialized) {
      bridge.waitForAllExecuted()
    }

    remoteCommunicator.delete()
  }

  protected fun assertSettingsPushed(build: SettingsSnapshotBuilder.() -> Unit) {
    waitForSettingsPush { pushedSnap ->
      pushedSnap.assertSettingsSnapshot {
        build()
      }
    }
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
    assertTrue("File $file does not exist", file.exists())
    assertEquals("File $file has unexpected content", expectedContent, file.readText())
  }

  protected fun assertServerSnapshot(build: SettingsSnapshotBuilder.() -> Unit) {
    val pushedSnapshot = remoteCommunicator.getVersionOnServer()
    assertNotNull("Nothing has been pushed", pushedSnapshot)
    pushedSnapshot!!.assertSettingsSnapshot {
      build()
    }
  }

  private fun waitForSettingsPush(assertSnapshot: (SettingsSnapshot) -> Unit) {
    val pushedSnap = remoteCommunicator.awaitForPush()
    assertNotNull("Changes were not pushed", pushedSnap)
    assertSnapshot(pushedSnap!!)
  }
}

internal fun SettingsSyncBridge.waitForAllExecuted() {
  this.waitForAllExecuted(getDefaultTimeoutInSeconds(), TIMEOUT_UNIT)
}

internal fun CountDownLatch.wait(): Boolean {
  return this.await(getDefaultTimeoutInSeconds(), TIMEOUT_UNIT)
}

private fun isTestingAgainstRealCloudServer() = System.getenv("SETTINGS_SYNC_TEST_CLOUD") == "real"

private fun getDefaultTimeoutInSeconds(): Long = if (isTestingAgainstRealCloudServer()) 60 else 10