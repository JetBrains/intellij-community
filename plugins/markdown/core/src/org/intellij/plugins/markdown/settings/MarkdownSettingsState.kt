// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownSettingsState: BaseState() {
  var areInjectionsEnabled by property(true)
  var hideErrorsInCodeBlocks by property(false)
  var isEnhancedEditingEnabled by property(true)

  @get:XMap
  var enabledExtensions by map<String, Boolean>()

  var splitLayout by enum(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)
  var previewPanelProviderInfo by property(MarkdownSettings.defaultProviderInfo) { it == MarkdownSettings.defaultProviderInfo }
  var isVerticalSplit by property(true)
  var isAutoScrollEnabled by property(true)
  var isRunnerEnabled by property(true)

  var useCustomStylesheetPath by property(false)
  var customStylesheetPath by string()

  var useCustomStylesheetText by property(false)
  var customStylesheetText by string()

  var fontSize by property(MarkdownSettings.defaultFontSize)
  var fontFamily by string(MarkdownSettings.defaultFontFamily)
}
