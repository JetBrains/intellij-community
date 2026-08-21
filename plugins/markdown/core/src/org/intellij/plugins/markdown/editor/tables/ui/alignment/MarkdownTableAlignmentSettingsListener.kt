// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic
import org.intellij.plugins.markdown.editor.tables.ui.MarkdownTableInlayProvider
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun isMarkdownTableVisualAlignmentEnabled(editor: Editor): Boolean {
  return MarkdownCodeInsightSettings.getInstance().state.alignTableCellsVisually &&
         editor.getUserData(MarkdownTableInlayProvider.DISABLE_TABLE_INLAYS) != true
}

/** Fired when [MarkdownCodeInsightSettings.State.alignTableCellsVisually] changes. */
@ApiStatus.Internal
fun interface MarkdownTableAlignmentSettingsListener {
  fun alignTableCellsSettingChanged()

  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<MarkdownTableAlignmentSettingsListener> =
      Topic.create("MarkdownTableAlignmentSettingsChanged", MarkdownTableAlignmentSettingsListener::class.java)

    fun fireChanged() {
      ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).alignTableCellsSettingChanged()
    }
  }
}
