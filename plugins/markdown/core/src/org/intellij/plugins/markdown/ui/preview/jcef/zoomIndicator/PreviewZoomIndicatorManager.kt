// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.jcef.zoomIndicator

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.BalloonImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
class PreviewZoomIndicatorManager(project: Project, coroutineScope: CoroutineScope) {
  private val cancelBalloonRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private var balloon: Balloon? = null
  private var preview: WeakReference<MarkdownJCEFHtmlPanel> = WeakReference(null)

  init {
    project.messageBus.connect(coroutineScope).subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        if (event.place == ActionPlaces.POPUP && action is PreviewZoomIndicatorView.SettingsAction) {
          cancelCurrentPopup()
        }
      }
    })

    coroutineScope.launch(context = Dispatchers.EDT) {
      cancelBalloonRequests.collectLatest {
        val b = balloon ?: return@collectLatest
        b.getView().isHovered().collectLatest { isHovered ->
          if (!isHovered) {
            delay(5.seconds)
            cancelCurrentPopup()
          } else {
            check(cancelBalloonRequests.tryEmit(Unit))
          }
        }
      }
    }
  }

  fun createOrGetBalloon(preview: MarkdownJCEFHtmlPanel): Balloon? {
    val view = PreviewZoomIndicatorView(preview)
    val b = balloon
    if (this.preview.refersTo(preview) && (b as? BalloonImpl)?.isVisible == true) {
      b.getView().updateFontSize()
      return null
    }
    cancelCurrentPopup()
    val newUI = ExperimentalUI.isNewUI()
    val b2 = JBPopupFactory.getInstance().createBalloonBuilder(view)
      .setRequestFocus(false)
      .setShadow(true)
      .setFillColor(if (newUI) JBColor.namedColor("Toolbar.Floating.background", JBColor(0xEDEDED, 0x454A4D)) else view.background)
      .setBorderColor(if (newUI) JBColor.namedColor("Toolbar.Floating.borderColor", JBColor(0xEBECF0, 0x43454A)) else JBColor.border())
      .setShowCallout(false)
      .setFadeoutTime(0)
      .setHideOnKeyOutside(false)
      .createBalloon().apply { setAnimationEnabled(false) }
    balloon = b2
    check(cancelBalloonRequests.tryEmit(Unit))
    this.preview = WeakReference(preview)
    return b2
  }

  fun cancelCurrentPopup() {
    balloon?.hide()
    balloon = null
    preview.clear()
  }

  private fun Balloon.getView() = ((this as BalloonImpl).content as PreviewZoomIndicatorView)
}