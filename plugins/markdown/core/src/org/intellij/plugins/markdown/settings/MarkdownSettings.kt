// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.messages.Topic
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.intellij.plugins.markdown.ui.preview.jcef.JCEFHtmlPanelProvider

@Service(Service.Level.PROJECT)
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
    internal val defaultFontSize
      get() = JBCefApp.normalizeScaledSize((checkNotNull(AppEditorFontOptions.getInstance().state).FONT_SIZE + 0.5).toInt())

    internal val defaultFontFamily
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
  }
}
