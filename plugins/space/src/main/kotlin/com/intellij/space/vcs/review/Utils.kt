package com.intellij.space.vcs.review

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.util.IconLoader
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