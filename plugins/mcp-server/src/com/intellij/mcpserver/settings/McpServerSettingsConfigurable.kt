// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.createSseServerJsonEntry
import com.intellij.mcpserver.createStdioMcpServerJsonConfiguration
import com.intellij.mcpserver.impl.McpClientDetector
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.TextTransferable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.RestService.Companion.getLastFocusedOrOpenedProject
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.JComponent

class McpServerSettingsConfigurable : SearchableConfigurable {
  private var settingsPanel: DialogPanel? = null
  private var enabledCheckboxState: ComponentPredicate? = null

  override fun getDisplayName(): String = McpServerBundle.message("configurable.name.mcp.plugin")

  override fun createComponent(): JComponent {
    val settings = McpServerSettings.getInstance()

    val panel = panel {
      row {
        enabledCheckboxState = checkBox(McpServerBundle.message("enable.mcp.server")).bindSelected(settings.state::enableMcpServer).selected
      }
      panel {

        fun getLabelText(): String = if (McpServerService.getInstance().isRunning) McpServerBundle.message("mcp.server.is.running.on") else ""
        fun getLinkText(): String = if (McpServerService.getInstance().isRunning) McpServerService.getInstance().serverSseUrl else ""

        row {
          val label = label(getLabelText())
          val link = link(getLinkText()) {
            BrowserUtil.browse(it.actionCommand!!)
          }

          enabledCheckboxState!!.addListener {
            McpServerService.getInstance().settingsChanged(it)
            val isServerRunning = McpServerService.getInstance().isRunning
            label.component.text = if (isServerRunning) getLabelText() else ""
            link.component.text = if (isServerRunning) McpServerService.getInstance().serverSseUrl else ""
          }
        }

        McpClientDetector.detectGlobalMcpClients().forEach { mcpClient ->
          val json = mcpClient.json
          group(mcpClient.name) {
            row {
              if (mcpClient.isConfigured() && mcpClient.isPortCorrect()) {
                text(McpServerBundle.message("mcp.server.configured"))
              }
              if(mcpClient.isConfigured() && !mcpClient.isPortCorrect()) {
                text(McpServerBundle.message("mcp.server.configured.port.invalid"))
              }
            }
            row {
              button(McpServerBundle.message("autoconfigure.mcp.server"), { mcpClient.configure() })
              button(McpServerBundle.message("open.settings.json"), { openFileInEditor(mcpClient.configPath) })
              button(McpServerBundle.message("copy.mcp.server.stdio.configuration"), {
                CopyPasteManager.getInstance().setContents(TextTransferable(json.encodeToString(buildJsonObject {
                  put("jetbrains", json.encodeToJsonElement(mcpClient.getStdioConfig()))
                }) as CharSequence))
                showCopiedBallon(it)
              })
              if (mcpClient.isSSESupported()) {
                button(McpServerBundle.message("copy.mcp.server.sse.configuration"), {
                  CopyPasteManager.getInstance().setContents(TextTransferable(json.encodeToString(buildJsonObject {
                    put("jetbrains", json.encodeToJsonElement(mcpClient.getSSEConfig()))
                  }) as CharSequence))
                  showCopiedBallon(it)
                })
              }
            }
          }
        }
        group(McpServerBundle.message("mcp.general.client")){
          row{
            button(McpServerBundle.message("copy.mcp.server.sse.configuration"), {
              val json = createSseServerJsonEntry(McpServerService.getInstance().port)
              CopyPasteManager.getInstance().setContents(TextTransferable(json.toString() as CharSequence))
              showCopiedBallon(it)
            })
            button(McpServerBundle.message("copy.mcp.server.stdio.configuration"), {
              val json = createStdioMcpServerJsonConfiguration(McpServerService.getInstance().port, null)
              CopyPasteManager.getInstance().setContents(TextTransferable(json.toString() as CharSequence))
              showCopiedBallon(it)
            })
          }
        }

      }.visibleIf(enabledCheckboxState!!)
      panel {
        group(McpServerBundle.message("border.title.commands.execution")) {
          row {
            checkBox(McpServerBundle.message("checkbox.enable.brave.mode.skip.command.execution.confirmations")).bindSelected(settings.state::enableBraveMode)
          }
          row {
            icon(AllIcons.General.Warning)
            comment(McpServerBundle.message("text.warning.enabling.brave.mode.will.allow.terminal.commands.to.execute.without.confirmation.use.with.caution"))
          }
        }
      }.enabledIf(enabledCheckboxState!!)
    }

    settingsPanel = panel
    return panel
  }

  private fun showCopiedBallon(event: ActionEvent) {
    JBPopupFactory
      .getInstance()
      .createHtmlTextBalloonBuilder(McpServerBundle.message("json.configuration.copied.to.clipboard"), null, null, null)
      .createBalloon()
      .showInCenterOf(event.source as JComponent)
  }

  private fun openFileInEditor(filePath: Path) {
    val project = getLastFocusedOrOpenedProject()
    if (project == null) {
      return
    }
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath.toString())
    virtualFile?.let { file ->
      FileEditorManager.getInstance(project).openFile(file, true)
    }
  }

  override fun isModified(): Boolean {
    return settingsPanel?.isModified() ?: false
  }

  override fun apply() {
    settingsPanel?.apply()
  }

  override fun reset() {
    settingsPanel?.reset()
  }

  override fun disposeUIResources() {
    enabledCheckboxState?.let {
      val uiEnableValue = it()
      val settingsEnableValue = McpServerSettings.getInstance().state.enableMcpServer
      if (uiEnableValue != settingsEnableValue) {
        McpServerService.getInstance().settingsChanged(settingsEnableValue)
      }
    }
    settingsPanel = null
    enabledCheckboxState = null
  }

  override fun getId(): @NonNls String = "com.intellij.mcpserver.settings"
}