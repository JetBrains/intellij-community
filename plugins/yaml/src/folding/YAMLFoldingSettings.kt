// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.folding

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.yaml.YAMLBundle

@State(name = "YAMLFoldingSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class YAMLFoldingSettings : PersistentStateComponent<YAMLFoldingSettings?> {
  @JvmField
  var useAbbreviation: Boolean = true

  @JvmField
  var abbreviationLengthLimit: Int = 20


  override fun getState(): YAMLFoldingSettings {
    return this
  }

  override fun loadState(state: YAMLFoldingSettings) {
    XmlSerializerUtil.copyBean<YAMLFoldingSettings>(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): YAMLFoldingSettings = requireNotNull(ApplicationManager.getApplication().getService(YAMLFoldingSettings::class.java)) {
      "JsonFoldingSettings service is not available"
    }
  }
}

internal class YAMLFoldingOptionsProvider : UiDslUnnamedConfigurable.Simple(), CodeFoldingOptionsProvider {

  override fun Panel.createContent() {
    val settings = YAMLFoldingSettings.getInstance()

    group(YAMLBundle.message("YAMLFoldingSettings.title")) {
      row {
        val useAbbreviation = checkBox(YAMLBundle.message("YAMLFoldingSettings.use.abbreviation"))
          .bindSelected(settings::useAbbreviation)
          .gap(RightGap.SMALL)

        intTextField(1..Int.MAX_VALUE)
          .bindIntText(settings::abbreviationLengthLimit)
          .enabledIf(useAbbreviation.selected)
          .gap(RightGap.SMALL)

        label(YAMLBundle.message("YAMLFoldingSettings.abbreviation.units.of.measurement", settings.abbreviationLengthLimit))
      }
    }
  }
}
