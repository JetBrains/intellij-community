// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JLabel
import javax.swing.UIManager

internal object PythonPackageManagerUIHelpers {
  suspend fun <T> runPackagingOperationMaybeBackground(
    project: Project,
    errorSink: ErrorSink?,
    @NlsContexts.ProgressTitle title: String,
    operation: suspend (() -> PyResult<T>?),
  ): T? = withBackgroundProgress(project = project, title, cancellable = true) {
    runPackagingOperationMaybeShowErrorDialog(errorSink) {
      withContext(Dispatchers.Default) {

        operation()
      }
    }
  }

  private suspend fun <T> runPackagingOperationMaybeShowErrorDialog(
    errorSink: ErrorSink?,
    operation: suspend (() -> PyResult<T>?),
  ): T? {
    val pyResult = operation() ?: return null
    return pyResult.onFailure {
      errorSink?.emit(it)
    }.getOrNull()
  }


  suspend fun showBalloon(point: RelativePoint, @NlsContexts.DialogMessage text: String, style: BalloonStyle): Balloon =
    withContext(Dispatchers.EDT) {
      val content = JLabel()
      val (borderColor, fillColor) = when (style) {
        BalloonStyle.SUCCESS -> JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR to JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND
        BalloonStyle.INFO -> JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR to JBUI.CurrentTheme.Banner.INFO_BACKGROUND
        BalloonStyle.ERROR -> JBUI.CurrentTheme.Validator.errorBorderColor() to JBUI.CurrentTheme.Validator.errorBackgroundColor()
      }
      val balloonBuilder = JBPopupFactory.getInstance()
        .createBalloonBuilder(content)
        .setBorderInsets(UIManager.getInsets("Balloon.error.textInsets"))
        .setBorderColor(borderColor)
        .setFillColor(fillColor)
        .setHideOnClickOutside(true)
        .setHideOnFrameResize(false)
      content.text = text
      val balloon = balloonBuilder.createBalloon()
      balloon.show(point, Balloon.Position.below)
      balloon
    }

  enum class BalloonStyle { ERROR, INFO, SUCCESS }
}