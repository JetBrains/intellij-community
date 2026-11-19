// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.util.xmlb.annotations.XMap
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownSettingsState: BaseState() {
  var alignTableCellsVisually: Boolean by property(true)
  var areInjectionsEnabled: Boolean by property(true)
  var showProblemsInCodeBlocks: Boolean by property(false)
  var isStripTrailingSpacesOnSave: Boolean by property(false)

  @get:XMap
  var enabledExtensions: MutableMap<String, Boolean> by map()

  var splitLayout: TextEditorWithPreview.Layout by enum(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)
  var previewPanelProviderInfo: MarkdownHtmlPanelProvider.ProviderInfo? by property(null) { it == null }
  var isVerticalSplit: Boolean by property(true)
  var previewMode: MarkdownPreviewMode by enum(MarkdownPreviewMode.EDITOR)
  var isAutoScrollEnabled: Boolean by property(true)
  var isRunnerEnabled: Boolean by property(true)
  var useFileDirectoryForCommands: Boolean? by property(null) { it == null }
  var isFileGroupingEnabled: Boolean by property(false)

  var useCustomStylesheetPath: Boolean by property(false)
  var customStylesheetPath: String? by string()

  var useCustomStylesheetText: Boolean by property(false)
  var customStylesheetText: String? by string()

  var fontSize: Int by property(MarkdownSettings.defaultFontSize)
  var fontFamily: String? by string(MarkdownSettings.defaultFontFamily)
}
