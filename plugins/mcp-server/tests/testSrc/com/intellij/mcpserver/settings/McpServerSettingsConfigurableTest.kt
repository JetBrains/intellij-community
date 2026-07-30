// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.frontend.settings.McpServerSettingsConfigurable
import com.intellij.mcpserver.frontend.settings.TerminalPromotionSetting
import com.intellij.mcpserver.frontend.settings.openFileInEditor
import com.intellij.mcpserver.impl.McpServerTerminalPromotionDismissalState
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import javax.swing.JCheckBox
import kotlin.io.path.exists

@TestApplication
class McpServerSettingsConfigurableTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project by projectFixture

  private val projectRoot: Path
    get() = Path.of(checkNotNull(project.basePath))

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    McpServerTerminalPromotionDismissalState.showAgain()
    FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
  }

  @Test
  fun `openFileInEditor opens existing file`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val existingPath = projectRoot.resolve(".mcp/settings.json")
    val existingFile = runWriteAction {
      VfsUtil.createDirectories(existingPath.parent.toString()).findOrCreateFile(existingPath.fileName.toString())
    }

    openFileInEditor(existingPath, project)

    assertThat(FileEditorManager.getInstance(project).isFileOpen(existingFile)).isTrue()
  }

  @Test
  fun `openFileInEditor creates missing file and opens it`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val missingPath = projectRoot.resolve(".codex/config.toml")
    assertThat(missingPath.exists()).isFalse()

    openFileInEditor(missingPath, project)

    val createdFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(missingPath)
    assertThat(missingPath.exists()).isTrue()
    assertThat(createdFile).isNotNull()
    assertThat(FileEditorManager.getInstance(project).isFileOpen(createdFile!!)).isTrue()
  }

  @Test
  fun createMcpServerSettingsConfigurable(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = McpServerSettingsConfigurable()
    assertThat(configurable.getDisplayName()).isNotEmpty()
    assertThat(configurable.getId()).isEqualTo("com.intellij.mcpserver.settings")
  }

  @Test
  fun lifecycleMcpServerSettingsConfigurable(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = McpServerSettingsConfigurable()
    val component = configurable.createComponent()
    assertThat(component).isNotNull()

    configurable.reset()
    assertThat(configurable.isModified()).isFalse()

    configurable.disposeUIResources()
  }

  /**
   * The order the Settings dialog drives a page in, and the one it drives again when the page is reopened.
   * The page has to come back the same each time, because the dialog builds it afresh rather than keeping
   * the one it had.
   */
  @Test
  fun `the page can be driven through the settings cycle twice`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val settings = McpServerSettings.getInstance()
    val original = settings.enableBraveMode
    try {
      repeat(2) {
        val configurable = McpServerSettingsConfigurable()
        try {
          configurable.createComponent()
          configurable.reset()
          assertThat(configurable.isModified()).isFalse()

          settings.enableBraveMode = !settings.enableBraveMode
          assertThat(configurable.isModified()).isTrue()

          configurable.reset()
          assertThat(configurable.isModified()).isFalse()
        }
        finally {
          configurable.disposeUIResources()
        }
      }
    }
    finally {
      settings.enableBraveMode = original
    }
  }

  /** Disposing a page that was never shown has to be as complete as disposing one that was. */
  @Test
  fun `a page that was never shown still disposes cleanly`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = McpServerSettingsConfigurable()
    val component = configurable.createComponent()

    configurable.disposeUIResources()

    assertThat(component.hierarchyListeners).isEmpty()
  }

  @Test
  fun `a rejected consent never turns the server on`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val settings = McpServerSettings.getInstance()
    val original = settings.enableMcpServer
    settings.enableMcpServer = false
    val previousDialog = TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    val configurable = McpServerSettingsConfigurable()
    try {
      val component = configurable.createComponent()
      configurable.reset()
      val enableCheckBox = UIUtil.findComponentsOfType(component, JCheckBox::class.java)
        .single { it.text == McpServerBundle.message("enable.mcp.server") }
      assertThat(enableCheckBox.isSelected).isFalse()

      var modifiedWhileAsking: Boolean? = null
      TestDialogManager.setTestDialog(TestDialog {
        modifiedWhileAsking = configurable.isModified()
        Messages.NO
      })

      enableCheckBox.doClick()
      UIUtil.dispatchAllInvocationEvents()

      assertThat(modifiedWhileAsking).describedAs("the page while the consent is asked").isFalse()
      assertThat(configurable.isModified()).isFalse()
    }
    finally {
      TestDialogManager.setTestDialog(previousDialog)
      configurable.disposeUIResources()
      settings.enableMcpServer = original
    }
  }

  @Test
  fun `terminal promotion setting is the dismissal state said the other way round`() {
    McpServerTerminalPromotionDismissalState.showAgain()
    assertThat(TerminalPromotionSetting.isShown()).isTrue()

    TerminalPromotionSetting.setShown(false)
    assertThat(McpServerTerminalPromotionDismissalState.isDismissed()).isTrue()
    assertThat(TerminalPromotionSetting.isShown()).isFalse()

    TerminalPromotionSetting.setShown(true)
    assertThat(McpServerTerminalPromotionDismissalState.isDismissed()).isFalse()
    assertThat(TerminalPromotionSetting.isShown()).isTrue()
  }
}
