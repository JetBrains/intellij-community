// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.ui.treeStructure.ProjectViewUpdateCause
import com.intellij.util.application
import com.intellij.util.messages.Topic
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider

const val MARKDOWN_SETTINGS_FILE_NAME: String = "markdown.xml"

@Suppress("MismatchedLightServiceLevelAndCtor")
@Service(Service.Level.APP)
@State(name = "MarkdownSettings", storages = [(Storage(MARKDOWN_SETTINGS_FILE_NAME))])
class MarkdownSettings private constructor(private val legacyProject: Project?):
  SimplePersistentStateComponent<MarkdownSettingsState>(MarkdownSettingsState()) {
  constructor(): this(null)

  private val preferences: MarkdownSettingsState
    get() = if (legacyProject == null) state else getInstance().state

  var areInjectionsEnabled: Boolean
    get() = preferences.areInjectionsEnabled
    set(value) { preferences.areInjectionsEnabled = value }

  var showProblemsInCodeBlocks: Boolean
    get() = preferences.showProblemsInCodeBlocks
    set(value) { preferences.showProblemsInCodeBlocks = value }

  var isStripTrailingSpacesOnSave: Boolean
    get() = preferences.isStripTrailingSpacesOnSave
    set(value) { preferences.isStripTrailingSpacesOnSave = value }

  // Keep this property for binary compatibility with plugins.
  @Deprecated("The default layout setting is no longer available.")
  var splitLayout: TextEditorWithPreview.Layout
    get() = preferences.splitLayout
    set(value) { preferences.splitLayout = value }

  var previewPanelProviderInfo: MarkdownHtmlPanelProvider.ProviderInfo
    get() = preferences.previewPanelProviderInfo
    set(value) { preferences.previewPanelProviderInfo = value }

  var isVerticalSplit: Boolean
    get() = preferences.isVerticalSplit
    set(value) { preferences.isVerticalSplit = value }

  var isAutoScrollEnabled: Boolean
    get() = preferences.isAutoScrollEnabled
    set(value) { preferences.isAutoScrollEnabled = value }

  @Deprecated("Use MarkdownStylesheetSettings.getInstance(project).useCustomStylesheetPath")
  @Suppress("unused")
  var useCustomStylesheetPath: Boolean
    get() {
      val project = legacyProject ?: return state.useCustomStylesheetPath
      return MarkdownStylesheetSettings.getInstance(project).useCustomStylesheetPath
    }
    set(value) {
      if (legacyProject == null) state.useCustomStylesheetPath = value
      else MarkdownStylesheetSettings.getInstance(legacyProject).useCustomStylesheetPath = value
    }

  @Deprecated("Use MarkdownStylesheetSettings.getInstance(project).customStylesheetPath")
  @Suppress("unused")
  var customStylesheetPath: String?
    get() {
      val project = legacyProject ?: return state.customStylesheetPath
      return MarkdownStylesheetSettings.getInstance(project).customStylesheetPath
    }
    set(value) {
      if (legacyProject == null) state.customStylesheetPath = value
      else MarkdownStylesheetSettings.getInstance(legacyProject).customStylesheetPath = value
    }

  var useCustomStylesheetText: Boolean
    get() = preferences.useCustomStylesheetText
    set(value) { preferences.useCustomStylesheetText = value }

  var customStylesheetText: String?
    get() = preferences.customStylesheetText
    set(value) { preferences.customStylesheetText = value }

  var isFileGroupingEnabled: Boolean
    get() = preferences.isFileGroupingEnabled
    set(value) { preferences.isFileGroupingEnabled = value }

  var useFileDirectoryForCommands: Boolean?
    get() = preferences.useFileDirectoryForCommands
    set(value) { preferences.useFileDirectoryForCommands = value }

  var fontSize: Int
    get() = preferences.fontSize
    set(value) { preferences.fontSize = value }

  override fun noStateLoaded() {
    super.noStateLoaded()
    loadState(MarkdownSettingsState())
  }

  fun update(block: (MarkdownSettings) -> Unit) {
    if (legacyProject != null) {
      getInstance().update { block(this) }
      return
    }
    val wasFileGroupingEnabled = isFileGroupingEnabled
    val publisher = application.messageBus.syncPublisher(ChangeListener.TOPIC)
    publisher.beforeSettingsChanged(this)
    block(this)
    publisher.settingsChanged(this)
    if (wasFileGroupingEnabled != isFileGroupingEnabled) {
      for (project in getOpenedProjects()) {
        ProjectView.getInstance(project).refresh(ProjectViewUpdateCause.SETTINGS)
      }
    }
  }

  /** Replaces application defaults with project settings once per project. Non-default application values take priority. */
  internal suspend fun reconcileWithProject(project: Project) {
    val projectState = project.serviceAsync<LegacyMarkdownSettingsV1>().loadedState ?: return
    val properties = PropertiesComponent.getInstance(project)
    if (properties.getBoolean(PROJECT_SETTINGS_MIGRATED)) return
    reconcileWithProject(projectState)
    properties.setValue(PROJECT_SETTINGS_MIGRATED, true)
  }

  private fun reconcileWithProject(projectState: MarkdownSettingsState) {
    val defaults = MarkdownSettingsState()
    val reconciled = MarkdownSettingsState().apply {
      copyFrom(state)
      if (areInjectionsEnabled == defaults.areInjectionsEnabled) areInjectionsEnabled = projectState.areInjectionsEnabled
      if (showProblemsInCodeBlocks == defaults.showProblemsInCodeBlocks) showProblemsInCodeBlocks = projectState.showProblemsInCodeBlocks
      if (isStripTrailingSpacesOnSave == defaults.isStripTrailingSpacesOnSave) {
        isStripTrailingSpacesOnSave = projectState.isStripTrailingSpacesOnSave
      }
      if (splitLayout == defaults.splitLayout) splitLayout = projectState.splitLayout
      if (previewPanelProviderInfo == defaults.previewPanelProviderInfo) previewPanelProviderInfo = projectState.previewPanelProviderInfo
      if (isVerticalSplit == defaults.isVerticalSplit) isVerticalSplit = projectState.isVerticalSplit
      if (isAutoScrollEnabled == defaults.isAutoScrollEnabled) isAutoScrollEnabled = projectState.isAutoScrollEnabled
      if (isFileGroupingEnabled == defaults.isFileGroupingEnabled) isFileGroupingEnabled = projectState.isFileGroupingEnabled
      if (isRunnerEnabled == defaults.isRunnerEnabled) isRunnerEnabled = projectState.isRunnerEnabled
      if (useFileDirectoryForCommands == defaults.useFileDirectoryForCommands) {
        useFileDirectoryForCommands = projectState.useFileDirectoryForCommands
      }
      if (customStylesheetText == defaults.customStylesheetText && useCustomStylesheetText == defaults.useCustomStylesheetText) {
        customStylesheetText = projectState.customStylesheetText
        useCustomStylesheetText = projectState.useCustomStylesheetText
      }
      for ((id, enabled) in projectState.enabledExtensions) {
        if (id !in enabledExtensions) {
          enabledExtensions[id] = enabled
        }
      }
    }
    if (reconciled == state) return
    update { state.copyFrom(reconciled) }
  }

  interface ChangeListener {
    fun beforeSettingsChanged(settings: MarkdownSettings) {
    }

    fun settingsChanged(settings: MarkdownSettings) {
    }

    companion object {
      @Topic.AppLevel
      @JvmField
      val TOPIC: Topic<ChangeListener> = Topic("MarkdownSettingsChanged", ChangeListener::class.java, Topic.BroadcastDirection.NONE)
    }
  }

  @Service(Service.Level.PROJECT)
  private class ProjectSettingsAdapter(project: Project) {
    val settings = MarkdownSettings(project)
  }

  companion object {
    private const val PROJECT_SETTINGS_MIGRATED = "markdown.settings.migrated.v1"

    internal val defaultFontSize
      get() = (checkNotNull(AppEditorFontOptions.getInstance().state).FONT_SIZE + 0.5).toInt()

    internal val defaultFontFamily
      get() = checkNotNull(AppEditorFontOptions.getInstance().state).FONT_FAMILY

    @JvmStatic
    val defaultProviderInfo: MarkdownHtmlPanelProvider.ProviderInfo
      get() {
        return MarkdownHtmlPanelProvider.getProviders()
          .firstOrNull { it.isAvailable() == MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE }
          ?.providerInfo
               ?: MarkdownHtmlPanelProvider.getProviders().firstOrNull()?.providerInfo
               ?: MarkdownHtmlPanelProvider.ProviderInfo("Unavailable", "Unavailable")
      }

    @JvmStatic
    fun getInstance(): MarkdownSettings = service()

    @Deprecated("Use getInstance() for application preferences")
    @JvmStatic
    fun getInstance(project: Project): MarkdownSettings = project.service<ProjectSettingsAdapter>().settings

    suspend fun getInstanceAsync(): MarkdownSettings = serviceAsync()
  }
}
