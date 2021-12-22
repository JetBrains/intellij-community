package com.intellij.settingsSync

import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

@RunWith(JUnit4::class)
internal class GitSettingsLogTest {

  private val tempDirManager = TemporaryDirectory()
  private val disposableRule = DisposableRule()
  @Rule @JvmField val ruleChain: RuleChain = RuleChain.outerRule(tempDirManager).around(disposableRule)

  private lateinit var configDir: Path
  private lateinit var settingsSyncStorage: Path

  @Before
  fun setUp() {
    val mainDir = tempDirManager.createDir()
    configDir = mainDir.resolve("rootconfig").createDirectories()
    settingsSyncStorage = configDir.resolve("settingsSync")
  }

  @Test
  fun `copy files initially`() {
    val keymapContent = "keymapContent"
    val keymapsFolder = configDir / "keymaps"
    (keymapsFolder / "mykeymap.xml").createFile().writeText(keymapContent)
    val editorContent = "editorContent"
    val editorXml = (configDir / "options" / "editor.xml").createFile()
    editorXml.writeText(editorContent)

    val settingsLog = GitSettingsLog(settingsSyncStorage, configDir, disposableRule.disposable) {
      listOf(keymapsFolder, editorXml)
    }

    settingsLog.collectCurrentSnapshot().assertSettingsSnapshot {
      fileState("keymaps/mykeymap.xml", keymapContent)
      fileState("options/editor.xml", editorContent)
    }
  }
}