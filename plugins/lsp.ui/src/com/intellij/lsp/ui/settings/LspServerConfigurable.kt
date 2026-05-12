// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.settings

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.lsp.ui.LspUiBundle
import com.intellij.lsp.ui.settings.LspServerConfiguration.CommunicationMode
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.selected

internal class LspServerConfigurable(
  private val configuration: LspServerConfiguration,
) : BoundConfigurable(configuration.name.ifEmpty { LspUiBundle.message("lsp.settings.default.name") }) {

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        checkBox(LspUiBundle.message("lsp.settings.server.enable"))
          .bindSelected(configuration::enabled)
      }

      row(LspUiBundle.message("lsp.settings.server.name")) {
        textField()
          .bindText(configuration::name)
          .comment(LspUiBundle.message("lsp.settings.server.name.comment"))
          .focused()
      }

      separator()

      group(LspUiBundle.message("lsp.settings.server.configuration.section")) {
        lateinit var socketRadioButton: Cell<JBRadioButton>

        buttonsGroup {
          row(LspUiBundle.message("lsp.settings.server.mode")) {
            @Suppress("DialogTitleCapitalization")
            radioButton(LspUiBundle.message("lsp.settings.server.mode.stdio"), CommunicationMode.STDIO)
            @Suppress("DialogTitleCapitalization")
            socketRadioButton = radioButton(LspUiBundle.message("lsp.settings.server.mode.socket"), CommunicationMode.SOCKET)
          }
        }
          .bind<CommunicationMode>(
            getter = configuration::communicationMode,
            setter = { configuration.communicationMode = it }
          )

        row(LspUiBundle.message("lsp.settings.server.port")) {
          intTextField(0..65535)
            .bindIntText(configuration::socketPort)
            .columns(COLUMNS_SHORT)
        }
          .visibleIf(socketRadioButton.selected)

        row(LspUiBundle.message("lsp.settings.server.executable")) {
          textFieldWithBrowseButton(LspUiBundle.message("lsp.settings.server.executable.browse"))
            .bindText(configuration::executablePath)
            .comment(LspUiBundle.message("lsp.settings.server.executable.comment"))
            .align(AlignX.FILL)
            .resizableColumn()
          panel {}
        }

        row(LspUiBundle.message("lsp.settings.server.arguments")) {
          textField()
            .bindText(configuration::arguments)
            .comment(LspUiBundle.message("lsp.settings.server.arguments.comment"))
            .align(AlignX.FILL)
            .resizableColumn()
          panel {}
        }
      }

      group(LspUiBundle.message("lsp.settings.server.files.association.group")) {
        row {
          val patternsPanel = LspPatternsPanel().also { it.setPatterns(configuration.filePatterns) }
          cell(patternsPanel)
            .align(AlignX.FILL)
            .resizableColumn()
            .bind(
              componentGet = { it.getPatterns() },
              componentSet = { component, value -> component.setPatterns(value) },
              MutableProperty(getter = { configuration.filePatterns }, setter = { configuration.filePatterns = it })
            )
          panel {}
        }.topGap(TopGap.MEDIUM)
      }

      val advancedSection = collapsibleGroup(LspUiBundle.message("lsp.settings.server.advanced.group")) {
        row {
          label(LspUiBundle.message("lsp.settings.server.init"))
        }
        row {
          textArea()
            .bindText(configuration::initializationOptions)
            .rows(5)
            .comment(LspUiBundle.message("lsp.settings.server.init.comment"))
            .columns(COLUMNS_LARGE)
        }

        row(LspUiBundle.message("lsp.settings.server.environment.variables")) {
          cell(EnvironmentVariablesTextFieldWithBrowseButton())
            .bind(
              componentGet = { component -> component.data },
              componentSet = { component, data -> component.data = data },
              MutableProperty(getter = { configuration.envVars.get() }, setter = { configuration.envVars.set(it) })
            )
            .align(AlignX.FILL)
            .resizableColumn()
          panel {}
        }
      }
      advancedSection.expanded = false
    }
  }

  override fun getDisplayName(): String = configuration.name.ifEmpty { LspUiBundle.message("lsp.settings.default.name") }
}