// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.client.api.M2ChannelRecord
import circlet.m2.ChannelsVm
import circlet.platform.api.Ref
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.space.chat.model.api.SpaceChatHeaderDetails
import com.intellij.space.chat.ui.header.createComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

internal class SpaceChatPanel(
  private val project: Project,
  private val lifetime: Lifetime,
  private val parent: Disposable,
  private val channelsVm: ChannelsVm,
  private val chatRecord: Ref<M2ChannelRecord>,
  private val headerDetails: SpaceChatHeaderDetails
) : BorderLayoutPanel() {

  init {
    isOpaque = true
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground

    val contentComponent = createContentComponent()
    val scrollableContentPanel = ScrollPaneFactory.createScrollPane(
      contentComponent,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
      border = JBUI.Borders.empty()
      isOpaque = false
      viewport.isOpaque = false
    }
    addToCenter(scrollableContentPanel)
  }

  private fun createContentComponent(): JComponent {
    val contentPanel = SpaceChatContentPanel(project, lifetime, parent, channelsVm, chatRecord).apply {
      setLoadingText(SpaceBundle.message("chat.loading.text"))
    }

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(5, 20)

      val maxWidth = JBUI.scale(600)

      layout = MigLayout(LC().gridGap("0", "${JBUI.scale(10)}")
                           .insets("0", "0", "0", "0")
                           .flowY(),
                         AC().size(":$maxWidth:$maxWidth").gap("push"))
      add(headerDetails.createComponent(lifetime), CC().growX().minWidth(""))
      add(contentPanel, CC().grow().push().minWidth(""))
    }
  }
}