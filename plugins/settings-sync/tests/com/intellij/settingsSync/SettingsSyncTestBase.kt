package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.createDirectories
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal abstract class SettingsSyncTestBase {

  private val appRule = ApplicationRule()
  private val tempDirManager = TemporaryDirectory()
  private val disposableRule = DisposableRule()
  @Rule @JvmField val ruleChain: RuleChain = RuleChain.outerRule(tempDirManager).around(appRule).around(disposableRule)

  protected lateinit var application: ApplicationImpl
  protected lateinit var configDir: Path
  protected lateinit var remoteCommunicator: SettingsSyncTest.TestRemoteCommunicator
  protected lateinit var updateChecker: SettingsSyncUpdateChecker
  protected lateinit var bridge: SettingsSyncBridge

  protected val disposable: Disposable get() = disposableRule.disposable
  protected val settingsSyncStorage: Path get() = configDir.resolve("settingsSync")

  @Before
  fun setup() {
    application = ApplicationManager.getApplication() as ApplicationImpl
    val mainDir = tempDirManager.createDir()
    configDir = mainDir.resolve("rootconfig").createDirectories()
    remoteCommunicator = SettingsSyncTest.TestRemoteCommunicator()
  }

  @After
  fun cleanup() {
    bridge.waitForAllExecuted(10, TimeUnit.SECONDS)
  }
}