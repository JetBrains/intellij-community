// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.settings

import com.intellij.mermaid.MermaidBundle
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.util.application
import com.intellij.util.messages.Topic
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings

@Suppress("UnstableApiUsage")
class MermaidSettingsConfigurable : BoundSearchableConfigurable(
  MermaidBundle.message("mermaid.settings.name"),
  "mermaid.settings",
  _id = ID
) {
  private val settings
    get() = MermaidSettings.getInstance()

  companion object {
    const val ID = "Settings.Mermaid"
  }

  override fun createPanel(): DialogPanel {
    return panel {
      row(MermaidBundle.message("mermaid.settings.theme")) {
        comboBox(
          model = EnumComboBoxModel(MermaidSettingsState.Theme::class.java),
          renderer = SimpleListCellRenderer.create("") { it?.printableName ?: "" }
        )
          .bindItem(settings::theme.toNullableProperty())
          .onApply {
            application.messageBus.syncPublisher(MarkdownExtensionsSettings.ChangeListener.TOPIC)
              .extensionsSettingsChanged(fromSettingsDialog = false)

            application.messageBus.syncPublisher(ChangeListener.TOPIC)
              .mermaidSettingsChanged()
          }
      }
    }
  }

  fun interface ChangeListener {
    fun mermaidSettingsChanged()

    companion object {
      @Topic.AppLevel
      @JvmField
      val TOPIC = Topic.create("MermaidSettingsChanged", ChangeListener::class.java)
    }
  }
}
