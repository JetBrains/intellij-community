// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.folding

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.ui.dsl.builder.Panel
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


  override fun getState(): YAMLFoldingSettings? {
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

internal class YAMLFoldingOptionsProvider :
  BeanConfigurable<YAMLFoldingSettings>(YAMLFoldingSettings.getInstance(), YAMLBundle.message("YAMLFoldingSettings.title")),
  CodeFoldingOptionsProvider {

  override fun Panel.createContent() {
    group(YAMLBundle.message("YAMLFoldingSettings.title")) {
      row {
        val s = checkBox(YAMLBundle.message("YAMLFoldingSettings.use.abbreviation"))
          .bindSelected(instance::useAbbreviation)

        intTextField(1..Int.MAX_VALUE)
          .bindIntText(instance::abbreviationLengthLimit)
          .enabledIf(s.selected)

        label(YAMLBundle.message("YAMLFoldingSettings.abbreviation.units.of.measurement", instance.abbreviationLengthLimit))

      }
    }
  }
}
