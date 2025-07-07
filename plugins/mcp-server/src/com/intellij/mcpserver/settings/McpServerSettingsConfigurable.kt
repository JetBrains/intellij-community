// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.icons.AllIcons
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpserverIcons
import com.intellij.mcpserver.createSseServerJsonEntry
import com.intellij.mcpserver.createStdioMcpServerJsonConfiguration
import com.intellij.mcpserver.impl.McpClientDetector
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.ValueComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.util.ui.TextTransferable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.RestService.Companion.getLastFocusedOrOpenedProject
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

class McpServerSettingsConfigurable : SearchableConfigurable {
  private var settingsPanel: DialogPanel? = null
  private var enabledCheckboxState: ComponentPredicate? = null

  override fun getDisplayName(): String = McpServerBundle.message("configurable.name.mcp.plugin")

  override fun createComponent(): JComponent {
    val settings = McpServerSettings.getInstance()

    val panel = panel {
      row {
        text(McpServerBundle.message("mcp.settings.description"))
      }
      row {
        val checkboxWithValidation = CheckboxWithValidation(if (McpServerService.getInstance().isRunning) {
          McpServerBundle.message("enable.mcp.server.when.enabled")
        }
                                                            else {
          McpServerBundle.message("enable.mcp.server")
        }, ConsentValidator)
        cell(checkboxWithValidation).bind(componentGet = { it.isValidatedSelected }, componentSet = { component, value -> component.isValidatedSelected = value }, prop = MutableProperty(getter = { settings.state.enableMcpServer }, setter = { settings.state.enableMcpServer = it })).gap(RightGap.SMALL)
        enabledCheckboxState = ValueComponentPredicate(checkboxWithValidation.isValidatedSelected).also { predicate ->
          checkboxWithValidation.addPropertyChangeListener(CheckboxWithValidation.IS_VALIDATED_SELECTED_PROPERTY) { evt ->
            predicate.set(evt.newValue as Boolean)
          }
        }

        fun getLinkText(): String = if (McpServerService.getInstance().isRunning) McpServerService.getInstance().serverSseUrl else ""
        val link = browserLink(getLinkText(), getLinkText()).visibleIf(enabledCheckboxState!!)

        enabledCheckboxState!!.addListener {
          McpServerService.getInstance().settingsChanged(it)
          val isServerRunning = McpServerService.getInstance().isRunning
          link.component.text = if (isServerRunning) McpServerService.getInstance().serverSseUrl else ""
          checkboxWithValidation.text = if (isServerRunning) McpServerBundle.message("enable.mcp.server.when.enabled") else McpServerBundle.message("enable.mcp.server")
        }
      }.bottomGap(BottomGap.SMALL)

      row {
        comment(McpServerBundle.message("settings.explanation.when.server.disabled"))
      }.bottomGap(BottomGap.NONE).visibleIf(enabledCheckboxState!!.not())
      McpClientDetector.detectGlobalMcpClients().forEach {
        row {
          comment(" • " + it.name.displayName)
        }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).visibleIf(enabledCheckboxState!!.not())
      }


      group(McpServerBundle.message("settings.client.group"), indent = false) {
        row {
          comment(McpServerBundle.message("settings.setup.description"))
        }
        indent {
          McpClientDetector.detectGlobalMcpClients().forEach { mcpClient ->
            val isConfigured = ValueComponentPredicate(mcpClient.isConfigured() ?: false)
            val isPortCorrect = ValueComponentPredicate(mcpClient.isPortCorrect())
            val json = mcpClient.json
            row {
              text(mcpClient.name.displayName)
            }.topGap(TopGap.SMALL)
            val autoconfiguredPressed = ValueComponentPredicate(false)
            val errorDuringConfiguration = ValueComponentPredicate(false)
            val configExists = ValueComponentPredicate(mcpClient.configPath.exists() && mcpClient.configPath.isRegularFile())
            row {
              cell(JBOptionButton(object : AbstractAction(McpServerBundle.message("autoconfigure.mcp.server")) {
                override fun actionPerformed(e: ActionEvent?) {
                  runCatching {
                    mcpClient.configure()
                  }.onFailure {
                    thisLogger().info(it)
                    errorDuringConfiguration.set(true)
                  }.onSuccess {
                    errorDuringConfiguration.set(false)
                    isConfigured.set(true)
                    isPortCorrect.set(true)
                    configExists.set(true)
                    autoconfiguredPressed.set(true)
                  }
                }
              }, options = arrayOf(object : AbstractAction(McpServerBundle.message("open.settings.json")) {
                override fun actionPerformed(e: ActionEvent?) {
                  openFileInEditor(mcpClient.configPath)
                }
              }, object : AbstractAction(McpServerBundle.message("copy.mcp.server.configuration")) {
                override fun actionPerformed(e: ActionEvent?) {
                  CopyPasteManager.getInstance().setContents(TextTransferable(json.encodeToString(buildJsonObject {
                    put("jetbrains", json.encodeToJsonElement(mcpClient.getConfig()))
                  }) as CharSequence))
                  if (e != null) showCopiedBallon(e)
                }
              })))
              icon(McpserverIcons.Expui.StatusEnabled).gap(RightGap.SMALL).visibleIf(isConfigured.and(isPortCorrect).and(autoconfiguredPressed.not()))
              text(McpServerBundle.message("mcp.server.configured")).visibleIf(isConfigured.and(isPortCorrect).and(autoconfiguredPressed.not()))

              icon(McpserverIcons.Expui.StatusEnabled).gap(RightGap.SMALL).visibleIf(autoconfiguredPressed)
              text(McpServerBundle.message("mcp.server.client.restart.info.settings")).visibleIf(autoconfiguredPressed)

              icon(McpserverIcons.Expui.StatusDisabled).gap(RightGap.SMALL).visibleIf(isConfigured.not())
              comment(McpServerBundle.message("mcp.server.not.configured")).visibleIf(isConfigured.not())

              icon(AllIcons.General.Error).gap(RightGap.SMALL).visibleIf(isConfigured.and(isPortCorrect.not()))
              comment(McpServerBundle.message("mcp.server.configured.port.invalid")).visibleIf(isConfigured.and(isPortCorrect.not()))

              icon(AllIcons.General.Error).gap(RightGap.SMALL).visibleIf(errorDuringConfiguration)
              comment(McpServerBundle.message("mcp.server.client.autoconfig.error")).visibleIf(errorDuringConfiguration)
            }
          }
        }
      }.visibleIf(enabledCheckboxState!!)

      group(McpServerBundle.message("mcp.general.client"), indent = false) {
        row {
          comment(McpServerBundle.message("settings.comment.manual.config"))
        }
        indent {
          row {
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

      group(McpServerBundle.message("border.title.terminal.commands")) {
        row {
          checkBox(McpServerBundle.message("checkbox.enable.brave.mode.skip.command.execution.confirmations")).comment(McpServerBundle.message("text.warning.enabling.brave.mode.will.allow.terminal.commands.to.execute.without.confirmation.use.with.caution")).bindSelected(settings.state::enableBraveMode)
        }
      }.visibleIf(enabledCheckboxState!!)
    }

    settingsPanel = panel
    return panel
  }

  private fun showCopiedBallon(event: ActionEvent) {
    JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(McpServerBundle.message("json.configuration.copied.to.clipboard"), null, null, null).createBalloon().showInCenterOf(event.source as JComponent)
  }

  private fun openFileInEditor(filePath: Path) {
    val project = getLastFocusedOrOpenedProject()
    if (project == null) {
      return
    }
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.toString())
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

private class CheckboxWithValidation(@Nls checkboxText: String, var validator: CheckboxValidator) : JCheckBox() {
  companion object {
    const val IS_VALIDATED_SELECTED_PROPERTY: String = "isValidatedSelected"
  }

  var isValidatedSelected: Boolean = false
    set(value) {
      val oldValue = field
      field = value
      isSelected = value
      firePropertyChange(IS_VALIDATED_SELECTED_PROPERTY, oldValue, value)
    }

  init {
    this.text = checkboxText
    addActionListener {
      val newValue = isSelected
      isSelected = isValidatedSelected
      SwingUtilities.invokeLater {
        if (validator.isValidNewValue(newValue)) {
          isValidatedSelected = newValue
        }
      }
    }
  }
}

private object ConsentValidator : CheckboxValidator {
  override fun isValidNewValue(isSelected: Boolean): Boolean = if (isSelected) {
    MessageDialogBuilder.yesNo(McpServerBundle.message("dialog.title.mcp.server.consent"), McpServerBundle.message("dialog.message.mcp.server.consent")).ask(getLastFocusedOrOpenedProject())
  }
  else true
}

private interface CheckboxValidator {
  fun isValidNewValue(isSelected: Boolean): Boolean
}