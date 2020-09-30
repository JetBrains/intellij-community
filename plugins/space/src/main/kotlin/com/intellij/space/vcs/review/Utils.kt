package com.intellij.space.vcs.review

import circlet.client.drafts
import circlet.code.api.CodeReviewWithCount
import circlet.m2.ChannelsVm
import circlet.m2.channel.M2DraftsVm
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.space.chat.editor.SpaceChatFile
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.util.ui.codereview.BaseHtmlEditorPane
import com.intellij.util.ui.codereview.InlineIconButton
import icons.SpaceIcons

internal class HtmlEditorPane : BaseHtmlEditorPane(SpaceIcons::class.java)

internal fun editIconButton(tooltip: String? = null,
                            shortcut: ShortcutSet? = null): InlineIconButton = InlineIconButton(
  AllIcons.General.Inline_edit,
  AllIcons.General.Inline_edit_hovered,
  IconLoader.getDisabledIcon(AllIcons.General.Inline_edit),
  tooltip,
  shortcut
)

internal fun openReviewInEditor(project: Project, reviewWithCount: CodeReviewWithCount) {
  val review = reviewWithCount.review.resolve()
  val chatRef = review.feedChannel ?: return
  val workspace = space.workspace.value ?: return
  val client = workspace.client
  val completionVm = workspace.completion
  val chatFile = SpaceChatFile(
    "space-review/${review.key}",
    SpaceBundle.message("review.chat.editor.tab.name", review.key, review.title),
    ChannelsVm(client, workspace.me, completionVm, M2DraftsVm(client, completionVm), workspace.featureFlags.featureFlags),
    chatRef
  )
  FileEditorManager.getInstance(project).openFile(chatFile, false)
}