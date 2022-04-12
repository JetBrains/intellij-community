package com.intellij.settingsSync

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@RunWith(JUnit4::class)
internal class SettingsSyncFlowTest : SettingsSyncTestBase() {

  fun initSettingsSync() {
    val ideMediator = MockSettingsSyncIdeMediator()
    val controls = SettingsSyncMain.init(application, disposable, settingsSyncStorage, configDir, remoteCommunicator, ideMediator)
    updateChecker = controls.updateChecker
    bridge = controls.bridge
    bridge.initialize(SettingsSyncBridge.InitMode.JustInit)
  }

  @Test fun `settings modified between IDE sessions should be logged`() {
    // emulate first session with initialization
    val fileName = "options/laf.xml"
    val file = configDir.resolve(fileName)
    file.parent.createDirectories()
    file.createFile()
    file.writeText("LaF Initial")
    val log = GitSettingsLog(settingsSyncStorage, configDir, disposable, MockSettingsSyncIdeMediator.getAllFilesFromSettings(configDir))
    log.initialize()

    // modify between sessions
    val contentBetweenSessions = "LaF Initial"
    file.writeText(contentBetweenSessions)

    initSettingsSync()

    assertSettingsPushed {
      fileState(fileName, contentBetweenSessions)
    }
  }
}