// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.settings

import com.intellij.codeInsight.template.impl.TemplateEditorUtil
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.json.JsonLanguage
import com.intellij.lsp.ui.LspUiBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspPluginServerConfiguration
import com.intellij.platform.lsp.api.LspIntegrationSettingsProvider
import com.intellij.platform.lsp.api.LspServerEnvironmentData
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.JBDimension

internal class PluginLspServerConfigurable(
  private val project: Project,
  var configuration: LspPluginServerConfiguration,
  private val pluginDescriptor: PluginDescriptor,
) : BoundConfigurable(configuration.name) {
  private lateinit var initializationOptionsEditor: Editor

  override fun createPanel(): DialogPanel {
    val jsonFile = PsiFileFactory.getInstance(project).createFileFromText(
      "dummy.json",
      JsonLanguage.INSTANCE,
      configuration.initializationOptions,
      true,
      false,
    )
    val jsonDocument = runReadActionBlocking { PsiDocumentManager.getInstance(project).getDocument(jsonFile) }
    initializationOptionsEditor = TemplateEditorUtil.createEditor(false, jsonDocument, project)
    initializationOptionsEditor.settings.additionalLinesCount = 0
    initializationOptionsEditor.component.preferredSize = JBDimension(200, 100)

    return panel {
      row {
        checkBox(LspUiBundle.message("lsp.settings.server.enable"))
          .applyToComponent {
            isSelected = true
            isEnabled = false
          }
      }

      row(LspUiBundle.message("lsp.settings.server.name")) {
        textField()
          .applyToComponent {
            text = configuration.name
            isEditable = false
            isEnabled = false
          }
      }

      row(LspUiBundle.message("lsp.settings.server.provided.by")) {
        link(pluginDescriptor.name) {
          PluginManagerConfigurable.showPluginConfigurable(project, listOf(pluginDescriptor.pluginId))
        }
      }

      separator()

      group(LspUiBundle.message("lsp.settings.server.configuration.section")) {
        row(LspUiBundle.message("lsp.settings.server.executable")) {
          textFieldWithBrowseButton(LspUiBundle.message("lsp.settings.server.executable.browse"))
            .applyToComponent {
              text = LspUiBundle.message("lsp.settings.server.executable.provided.by.plugin")
              isEditable = false
              isEnabled = false
            }
            .comment(LspUiBundle.message("lsp.settings.server.executable.comment"))
            .align(AlignX.FILL)
            .resizableColumn()
          panel {}
        }

        row(LspUiBundle.message("lsp.settings.server.arguments")) {
          textField()
            .bindText(
              { ParametersListUtil.join(configuration.arguments) },
              { configuration = configuration.copy(arguments = ParametersListUtil.parse(it)) },
            )
            .comment(LspUiBundle.message("lsp.settings.server.arguments.comment"))
            .align(AlignX.FILL)
            .resizableColumn()
          panel {}
        }

        row(LspUiBundle.message("lsp.settings.server.environment.variables")) {
          cell(EnvironmentVariablesTextFieldWithBrowseButton(project))
            .bind(
              componentGet = { component ->
                component.data.let {
                  LspServerEnvironmentData(
                    variables = it.envs,
                    passParentEnvironment = it.isPassParentEnvs,
                  )
                }
              },
              componentSet = { component, environment ->
                component.data = EnvironmentVariablesData.create(environment.variables, environment.passParentEnvironment)
              },
              MutableProperty(
                getter = { configuration.environmentVariables },
                setter = { configuration = configuration.copy(environmentVariables = it) },
              )
            )
            .align(AlignX.FILL)
            .resizableColumn()
          panel {}
        }
      }

      group(LspUiBundle.message("lsp.settings.server.files.association.group")) {
        row {
          val patternsPanel = LspPatternsPanel().also { it.setPatterns(configuration.filePatterns.joinToString(";")) }
          cell(patternsPanel)
            .align(AlignX.FILL)
            .resizableColumn()
            .bind(
              componentGet = { it.getPatterns().split(";").filter(String::isNotBlank) },
              componentSet = { component, value -> component.setPatterns(value.joinToString(";")) },
              MutableProperty(
                getter = { configuration.filePatterns },
                setter = { configuration = configuration.copy(filePatterns = it) },
              )
            )
          panel {}
        }.topGap(TopGap.MEDIUM)
      }

      row {
        label(LspUiBundle.message("lsp.settings.server.init"))
      }
      row {
        cell(initializationOptionsEditor.component)
          .align(Align.FILL)
          .resizableColumn()
          .comment(LspUiBundle.message("lsp.settings.server.init.comment"))
          .onApply { configuration = configuration.copy(initializationOptions = initializationOptionsEditor.document.text) }
          .onIsModified { configuration.initializationOptions != initializationOptionsEditor.document.text }
          .onReset {
            WriteAction.run<Throwable> { initializationOptionsEditor.document.setText(configuration.initializationOptions) }
          }
        panel {}
      }.resizableRow()
    }
  }

  override fun disposeUIResources() {
    if (::initializationOptionsEditor.isInitialized) {
      EditorFactory.getInstance().releaseEditor(initializationOptionsEditor)
    }
  }
}

internal class PluginLspServerNamedConfigurable(
  private val project: Project,
  private val provider: LspIntegrationSettingsProvider,
  private val configuration: LspPluginServerConfiguration,
  private val pluginDescriptor: PluginDescriptor,
  private val updateTree: Runnable,
) : NamedConfigurable<LspPluginServerConfiguration>(), Disposable {
  private var serverConfigurable: PluginLspServerConfigurable? = null

  override fun setDisplayName(name: String) {
  }

  override fun getEditableObject(): LspPluginServerConfiguration = configuration

  override fun getBannerSlogan(): String = configuration.name

  override fun createOptionsPanel() = JBScrollPane(getServerConfigurable().createComponent())

  override fun getDisplayName(): String = configuration.name

  override fun apply() {
    getServerConfigurable().apply()
    updateTree.run()
  }

  override fun isModified(): Boolean = getServerConfigurable().isModified

  override fun reset() {
    getServerConfigurable().reset()
  }

  @Throws(ConfigurationException::class)
  fun applyConfiguration(): Class<out LspIntegrationProvider>? {
    val edited = serverConfigurable?.configuration ?: return null
    val settings = LspIntegrationSettingsImpl.getInstance(project)
    if (edited == settings.getPluginConfiguration(provider)) {
      return null
    }

    settings.updatePluginConfiguration(provider.serverId, edited)
    return provider.integrationProviderClass
  }

  override fun dispose() {
    serverConfigurable?.disposeUIResources()
    serverConfigurable = null
  }

  private fun getServerConfigurable(): PluginLspServerConfigurable {
    if (serverConfigurable == null) {
      serverConfigurable = PluginLspServerConfigurable(project, configuration, pluginDescriptor)
    }
    return serverConfigurable!!
  }
}
