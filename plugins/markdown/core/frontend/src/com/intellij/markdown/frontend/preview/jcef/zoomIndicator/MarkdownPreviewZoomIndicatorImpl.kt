// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.preview.jcef.zoomIndicator

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.awt.RelativePoint
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewBrowserActions
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewZoomIndicator
import javax.swing.JComponent

internal class MarkdownPreviewZoomIndicatorImpl(private val project: Project) : MarkdownPreviewZoomIndicator {
  override fun show(preview: MarkdownPreviewBrowserActions, component: JComponent) {
    val balloon = project.service<PreviewZoomIndicatorManager>().createOrGetBalloon(preview)
    balloon?.show(RelativePoint.getSouthOf(component), Balloon.Position.below)
  }
}
