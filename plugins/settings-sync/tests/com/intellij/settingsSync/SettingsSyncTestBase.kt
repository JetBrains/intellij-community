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
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import java.nio.file.Path
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

    remoteCommunicator = if (System.getenv("SETTINGS_SYNC_TEST_CLOUD") == "real") {
      System.setProperty(CloudConfigServerCommunicator.URL_PROPERTY, CloudConfigServerCommunicator.DEFAULT_PRODUCTION_URL)
      TestCloudConfigRemoteCommunicator()
    } else {
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
      bridge.waitForAllExecuted(10, TimeUnit.SECONDS)
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

  private fun waitForSettingsPush(assertSnapshot: (SettingsSnapshot) -> Unit) {
    val pushedSnap = remoteCommunicator.awaitForPush()
    Assert.assertNotNull("Changes were not pushed", pushedSnap)
    assertSnapshot(pushedSnap!!)
  }
}