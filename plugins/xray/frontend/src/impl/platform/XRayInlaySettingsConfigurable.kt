// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.frontend.impl.platform

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.settings.INLAY_ID
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsConfigurable
import com.intellij.codeInsight.hints.settings.InlaySettingsConfigurableApi
import com.intellij.lang.Language
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import java.util.function.Predicate
import javax.swing.JComponent

internal class XRayInlaySettingsConfigurable(val project: Project) : Configurable, SearchableConfigurable, Configurable.NoScroll, Configurable.VariableProjectAppLevel, InlaySettingsConfigurableApi {

  private val panel: XRayInlaySettingsPanel by lazy  { XRayInlaySettingsPanel(project) }

  override fun createComponent(): JComponent {
    return panel
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return panel.tree
  }

  override fun isModified(): Boolean {
    return panel.isModified()
  }

  override fun apply() {
    panel.apply()
  }

  override fun reset() {
    panel.reset()
  }

  override fun enableSearch(option: String?): Runnable? {
    return panel.enableSearch(option)
  }

  override fun selectModel(language: Language, selector: Predicate<InlayProviderSettingsModel>?) {
    panel.selectModel(language, selector)
  }

  override fun getDisplayName(): String = CodeInsightBundle.message("settings.inlay.hints.panel.name")

  override fun getId(): String = INLAY_ID

  override fun getHelpTopic(): String = "settings.inlays"

  override fun isProjectLevel(): Boolean = false

  override fun getOriginalClass(): Class<*> {
    return InlaySettingsConfigurable::class.java
  }
}
