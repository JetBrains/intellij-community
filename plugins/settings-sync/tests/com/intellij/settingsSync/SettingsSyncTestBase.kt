package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.createDirectories
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal val TIMEOUT_UNIT = TimeUnit.SECONDS

internal abstract class SettingsSyncTestBase {

  private val appRule = ApplicationRule()
  private val tempDirManager = TemporaryDirectory()
  private val disposableRule = DisposableRule()
  @Rule @JvmField val ruleChain: RuleChain = RuleChain.outerRule(tempDirManager).around(appRule).around(disposableRule)

  protected lateinit var application: ApplicationImpl
  protected lateinit var configDir: Path
  protected lateinit var remoteCommunicator: MockRemoteCommunicator
  protected lateinit var updateChecker: SettingsSyncUpdateChecker
  protected lateinit var bridge: SettingsSyncBridge

  protected val disposable: Disposable get() = disposableRule.disposable
  protected val settingsSyncStorage: Path get() = configDir.resolve("settingsSync")

  @Before
  fun setup() {
    application = ApplicationManager.getApplication() as ApplicationImpl
    val mainDir = tempDirManager.createDir()
    configDir = mainDir.resolve("rootconfig").createDirectories()
    remoteCommunicator = MockRemoteCommunicator()
  }

  @After
  fun cleanup() {
    if (::bridge.isInitialized) {
      bridge.waitForAllExecuted(10, TimeUnit.SECONDS)
    }
  }

  protected fun assertSettingsPushed(build: SettingsSnapshotBuilder.() -> Unit) {
    waitForSettingsPush { pushedSnap ->
      pushedSnap.assertSettingsSnapshot {
        build()
      }
    }
  }

  private fun waitForSettingsPush(assertSnapshot: (SettingsSnapshot) -> Unit) {
    val cdl = CountDownLatch(1)
    remoteCommunicator.pushedLatch = cdl
    Assert.assertTrue("Didn't await until changes are pushed", cdl.await(5, TIMEOUT_UNIT))

    val pushedSnap = remoteCommunicator.pushed
    Assert.assertNotNull("Changes were not pushed", pushedSnap)
    assertSnapshot(pushedSnap!!)
  }

}