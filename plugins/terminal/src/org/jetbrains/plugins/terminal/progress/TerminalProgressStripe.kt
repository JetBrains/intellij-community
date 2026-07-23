// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.progress

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.util.ProgressBarUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.ProgressBarLoadingDecorator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JProgressBar

@ApiStatus.Internal
class TerminalProgressStripe(
  targetComponent: JComponent,
  private val parentDisposable: Disposable,
) : JBPanel<TerminalProgressStripe>(BorderLayout()), TerminalProgressListener {
  private val contentPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
    isOpaque = false
    add(targetComponent, BorderLayout.CENTER)
  }
  private val decorator = ProgressBarLoadingDecorator(contentPanel, parentDisposable, 0)

  init {
    isOpaque = false
    decorator.loadingText = ""
    add(decorator.component, BorderLayout.CENTER)
    configureProgressBar()
    progressChanged(TerminalProgressState.NONE)
  }

  override fun progressChanged(progressState: TerminalProgressState) {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      applyProgressState(progressState)
    }
    else {
      application.invokeLater {
        if (!Disposer.isDisposed(parentDisposable)) {
          applyProgressState(progressState)
        }
      }
    }
  }

  private fun applyProgressState(progressState: TerminalProgressState) {
    val progressBar = progressBar
    progressBar.putClientProperty(ProgressBarUtil.STATUS_KEY, progressState.progressBarStatus)
    progressBar.isIndeterminate = progressState.isIndeterminate
    if (!progressState.isIndeterminate) {
      progressBar.value = progressState.percent.coerceIn(0, 100)
    }

    if (progressState.isVisible) {
      decorator.startLoading()
    }
    else {
      progressBar.isIndeterminate = false
      progressBar.value = 100
      decorator.stopLoading()
    }
  }

  private fun configureProgressBar() {
    val progressBar = progressBar
    progressBar.minimum = 0
    progressBar.maximum = 100
    progressBar.isStringPainted = false
    progressBar.accessibleContext.accessibleName = TerminalBundle.message("terminal.progress.accessible.name")
  }

  private val progressBar: JProgressBar
    get() = decorator.progressBar
}

val TerminalProgressState.progressBarStatus: String?
  @ApiStatus.Internal
  get() = when (status) {
    TerminalProgressStatus.ERROR -> ProgressBarUtil.FAILED_VALUE
    TerminalProgressStatus.WARNING -> ProgressBarUtil.WARNING_VALUE
    else -> null
  }
