// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.messages.Topic
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.intellij.plugins.markdown.ui.preview.jcef.JCEFHtmlPanelProvider

@State(name = "MarkdownSettings", storages = [(Storage("markdown.xml"))])
class MarkdownSettings(internal val project: Project): SimplePersistentStateComponent<MarkdownSettingsState>(MarkdownSettingsState()) {
  var areInjectionsEnabled
    get() = state.areInjectionsEnabled
    set(value) { state.areInjectionsEnabled = value }

  var showProblemsInCodeBlocks
    get() = state.showProblemsInCodeBlocks
    set(value) { state.showProblemsInCodeBlocks = value }

  var splitLayout
    get() = state.splitLayout
    set(value) { state.splitLayout = value }

  var previewPanelProviderInfo
    get() = state.previewPanelProviderInfo
    set(value) { state.previewPanelProviderInfo = value }

  var isVerticalSplit
    get() = state.isVerticalSplit
    set(value) { state.isVerticalSplit = value }

  var isAutoScrollEnabled
    get() = state.isAutoScrollEnabled
    set(value) { state.isAutoScrollEnabled = value }

  var useCustomStylesheetPath
    get() = state.useCustomStylesheetPath
    set(value) { state.useCustomStylesheetPath = value }

  var customStylesheetPath
    get() = state.customStylesheetPath
    set(value) { state.customStylesheetPath = value }

  var useCustomStylesheetText
    get() = state.useCustomStylesheetText
    set(value) { state.useCustomStylesheetText = value }

  var customStylesheetText
    get() = state.customStylesheetText
    set(value) { state.customStylesheetText = value }

  var isFileGroupingEnabled
    get() = state.isFileGroupingEnabled
    set(value) { state.isFileGroupingEnabled = value }

  var fontSize
    get() = state.fontSize
    set(value) { state.fontSize = value }

  var fontFamily
    get() = state.fontFamily
    set(value) { state.fontFamily = value }

  override fun loadState(state: MarkdownSettingsState) {
    val migrated = possiblyMigrateSettings(state)
    super.loadState(migrated)
  }

  override fun noStateLoaded() {
    super.noStateLoaded()
    loadState(MarkdownSettingsState())
  }

  @Synchronized
  fun update(block: (MarkdownSettings) -> Unit) {
    val publisher = project.messageBus.syncPublisher(ChangeListener.TOPIC)
    publisher.beforeSettingsChanged(this)
    block(this)
    publisher.settingsChanged(this)
  }

  private fun possiblyMigrateSettings(from: MarkdownSettingsState): MarkdownSettingsState {
    @Suppress("DEPRECATION")
    val old = MarkdownApplicationSettings.getInstance().takeIf { it.state != null }
    val migration = MarkdownSettingsMigration.getInstance(project)
    if (old == null || migration.state.stateVersion == 1) {
      return from
    }
    logger.info("Migrating Markdown settings")
    val migrated = MarkdownSettingsState()
    with(migrated) {
      old.markdownPreviewSettings.let {
        previewPanelProviderInfo = it.htmlPanelProviderInfo
        splitLayout = it.splitEditorLayout
        isAutoScrollEnabled = it.isAutoScrollPreview
        isVerticalSplit = it.isVerticalSplit
      }
      old.markdownCssSettings.let {
        customStylesheetPath = it.customStylesheetPath.takeIf { _ -> it.isCustomStylesheetEnabled }
        customStylesheetText = it.customStylesheetText.takeIf { _ -> it.isTextEnabled }
        fontFamily = it.fontFamily
        fontSize = it.fontSize
      }
      enabledExtensions = old.extensionsEnabledState
      areInjectionsEnabled = !old.isDisableInjections
      showProblemsInCodeBlocks = !old.isHideErrors
      resetModificationCount()
    }
    migration.state.stateVersion = 1
    return migrated
  }

  interface ChangeListener {
    fun beforeSettingsChanged(settings: MarkdownSettings) {
    }

    fun settingsChanged(settings: MarkdownSettings) {
    }

    companion object {
      @Topic.ProjectLevel
      @JvmField
      val TOPIC = Topic("MarkdownSettingsChanged", ChangeListener::class.java, Topic.BroadcastDirection.NONE)
    }
  }

  companion object {
    private val logger = logger<MarkdownSettings>()

    val defaultFontSize
      get() = JBCefApp.normalizeScaledSize((checkNotNull(AppEditorFontOptions.getInstance().state).FONT_SIZE + 0.5).toInt())

    val defaultFontFamily
      get() = checkNotNull(AppEditorFontOptions.getInstance().state).FONT_FAMILY

    @JvmStatic
    val defaultProviderInfo: MarkdownHtmlPanelProvider.ProviderInfo
      get() {
        return when {
          JBCefApp.isSupported() -> JCEFHtmlPanelProvider().providerInfo
          else -> MarkdownHtmlPanelProvider.ProviderInfo("Unavailable", "Unavailable")
        }
      }

    @JvmStatic
    fun getInstance(project: Project): MarkdownSettings = project.service()

    fun getInstanceForDefaultProject(): MarkdownSettings {
      return ProjectManager.getInstance().defaultProject.service()
    }
  }
}
