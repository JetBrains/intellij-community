// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import circlet.code.api.CodeReviewRecord
import circlet.platform.api.Ref
import circlet.platform.client.property
import circlet.platform.client.resolve
import circlet.workspaces.Workspace
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.space.chat.model.impl.SpaceChatReviewHeaderDetails
import com.intellij.space.editor.SpaceVirtualFilesManager
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.util.ui.codereview.BaseHtmlEditorPane
import com.intellij.util.ui.codereview.InlineIconButton
import icons.SpaceIcons
import runtime.reactive.property.map

internal open class HtmlEditorPane : BaseHtmlEditorPane(SpaceIcons::class.java)

internal fun editIconButton(@NlsContexts.Tooltip tooltip: String? = null,
                            shortcut: ShortcutSet? = null): InlineIconButton = InlineIconButton(
  AllIcons.General.Inline_edit,
  AllIcons.General.Inline_edit_hovered,
  IconLoader.getDisabledIcon(AllIcons.General.Inline_edit),
  tooltip,
  shortcut
)

internal fun openReviewInEditor(
  project: Project,
  workspace: Workspace,
  projectInfo: SpaceProjectInfo,
  ref: Ref<CodeReviewRecord>
) {
  val review = ref.resolve()
  val chatRef = review.feedChannel ?: return
  val chatFile = project.service<SpaceVirtualFilesManager>().findOrCreateChatFile(
    review.key ?: review.id,
    "space-review/${review.key}",
    SpaceBundle.message("review.chat.editor.tab.name", review.key),
    SpaceBundle.message("review.chat.editor.tab.tooltip", review.key, review.title),
    workspace.chatVm.channels,
    chatRef,
  ) { editorLifetime ->
    val reviewProperty = review.property(workspace.client)
    val titleProperty = editorLifetime.map(reviewProperty) { it.title }
    val reviewStateProperty = editorLifetime.map(reviewProperty) { it.state }
    SpaceChatReviewHeaderDetails(projectInfo, reviewStateProperty, titleProperty, review.key) // NON-NLS
  }
  FileEditorManager.getInstance(project).openFile(chatFile, true)
}