// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.jcef.zoomIndicator

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.miginfocom.swing.MigLayout
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor.Companion.PREVIEW_JCEF_PANEL
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.JLabel
import javax.swing.JPanel

class PreviewZoomIndicatorView(private val preview: MarkdownJCEFHtmlPanel) : JPanel(MigLayout("novisualpadding, ins 0")) {
  private val isHoveredFlow = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val fontSizeLabel = JLabel(IdeBundle.message("action.reset.font.size.info", "000"))

  private val settingsButton = SettingsButton(SettingsAction())

  internal class SettingsAction: DumbAwareAction(IdeBundle.message("action.open.editor.settings.text"), "", AllIcons.General.Settings) {
    override fun actionPerformed(e: AnActionEvent) {
      val prj = e.project ?: return
      val searchName = MarkdownBundle.message("markdown.settings.preview.font.size")
      ShowSettingsUtilImpl.showSettingsDialog(prj, "Settings.Markdown", searchName)
    }
  }

  internal class SettingsButton(settingsAction: SettingsAction) : ActionButton(settingsAction, settingsAction.templatePresentation.clone(), ActionPlaces.POPUP, JBUI.size(22, 22)) {
    override fun performAction(e: MouseEvent?) {
      val event = AnActionEvent.createEvent(dataContext, myPresentation, myPlace, ActionUiKind.TOOLBAR, e)
      val actionManager = event.actionManager as ActionManagerEx
      actionManager.performWithActionCallbacks(myAction, event) { actionPerformed(event) }
    }
    override fun isShowing() = true
  }

  private inner class PatchedActionLink(action: AnAction, event: AnActionEvent) : AnActionLink(action, ActionPlaces.POPUP) {
    init {
      text = event.presentation.text
      autoHideOnDisable = false
      isEnabled = event.presentation.isEnabled
      event.presentation.addPropertyChangeListener {
        if (it.propertyName == Presentation.PROP_ENABLED) {
          isEnabled = it.newValue as Boolean
        }
      }
    }

    override fun uiDataSnapshot(sink: DataSink) {
      super.uiDataSnapshot(sink)
      sink[PREVIEW_JCEF_PANEL] = WeakReference(preview)
    }
    override fun isShowing() = true
  }

  private val resetLink = ActionManager.getInstance().getAction("Markdown.Preview.ResetFontSize").run {
    val dataContext = SimpleDataContext.builder().add(PREVIEW_JCEF_PANEL, WeakReference(preview)).build()
    val event = AnActionEvent.createEvent(dataContext, null, ActionPlaces.TOOLBAR,
                                          ActionUiKind.TOOLBAR, null)
    update(event)
    fontSizeLabel.addPropertyChangeListener {
      if (it.propertyName == "text") {
          update(event)
      }
    }

    PatchedActionLink(this, event)
  }

  init {
    check(isHoveredFlow.tryEmit(false))

    val mouseListener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) { check(isHoveredFlow.tryEmit(true)) }
      override fun mouseExited(e: MouseEvent?) { check(isHoveredFlow.tryEmit(false)) }
    }
    addMouseListener(mouseListener)
    resetLink.addMouseListener(mouseListener)
    settingsButton.addMouseListener(mouseListener)

    updateFontSize()

    add(fontSizeLabel, "wmin 100, gapbottom 1, gapleft 3")
    add(resetLink, "gapbottom 1")
    add(settingsButton)
  }

  fun updateFontSize() {
    fontSizeLabel.text = IdeBundle.message("action.reset.font.size.info", preview.getTemporaryFontSize())
  }

  fun isHovered(): Flow<Boolean> {
    return isHoveredFlow
  }
}