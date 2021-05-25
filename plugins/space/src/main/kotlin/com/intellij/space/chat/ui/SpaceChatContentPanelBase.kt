// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.client.api.M2ChannelRecord
import circlet.m2.ChannelsVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.Ref
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import org.jetbrains.annotations.Nls
import runtime.Ui
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent

internal abstract class SpaceChatContentPanelBase(
  private val lifetime: Lifetime,
  parent: Disposable,
  private val channelsVm: ChannelsVm,
  private val chatRecord: Ref<M2ChannelRecord>
) : BorderLayoutPanel() {
  private val loadingPanel = object : JBLoadingPanel(BorderLayout(), parent) {
    init {
      startLoading()
      isOpaque = false
    }

    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      return Dimension(size.width, size.height.takeIf { it != 0 } ?: JBUI.scale(80))
    }
  }

  init {
    isOpaque = true
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground

    addToCenter(loadingPanel)

    addHoveringSupport()
    launch(lifetime, Ui) {
      val chatVM = loadChatVM()
      onChatLoad(chatVM)
    }
  }

  private fun addHoveringSupport() {
    var lastHoveredMessagePanel: HoverableJPanel? = null
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent?) {
        e ?: return
        val component = UIUtil.getDeepestComponentAt(this@SpaceChatContentPanelBase, e.x, e.y) ?: return
        val messageComponent = ComponentUtil.getParentOfType(HoverableJPanel::class.java, component)
        if (messageComponent != lastHoveredMessagePanel) {
          lastHoveredMessagePanel?.hoverStateChanged(false)
          lastHoveredMessagePanel = messageComponent
          messageComponent?.hoverStateChanged(true)
        }
      }
    })
  }

  private suspend fun loadChatVM(): M2ChannelVm = channelsVm.channel(lifetime, chatRecord).also {
    it.awaitFullLoad(lifetime)
  }

  protected abstract fun onChatLoad(chatVm: M2ChannelVm)

  @RequiresEdt
  protected fun stopLoadingContent(contentComponent: JComponent) {
    if (loadingPanel.isLoading) {
      loadingPanel.add(contentComponent, BorderLayout.CENTER)
      loadingPanel.stopLoading()
      loadingPanel.revalidate()
      loadingPanel.repaint()
    }
  }

  fun setLoadingText(@Nls text: String) {
    loadingPanel.setLoadingText(text)
  }
}