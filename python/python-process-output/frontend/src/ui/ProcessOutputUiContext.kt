package com.intellij.python.processOutput.frontend.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.python.processOutput.frontend.ProcessOutputController
import com.intellij.python.processOutput.frontend.ProcessOutputControllerService
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import javax.swing.JPanel

internal class ProcessOutputUiContext(
  val project: Project,
  val rootPanel: JPanel,
  parentDisposable: Disposable,
) {
  val coroutineScope =
    project
      .service<ProcessOutputCoroutine>()
      .coroutineScope
      .childScope("Process Output Tool Window")
      .apply { Disposer.register(parentDisposable, asDisposable()) }

  val controller: ProcessOutputController
    get() = project.service<ProcessOutputControllerService>()
}

@Service(Service.Level.PROJECT)
private class ProcessOutputCoroutine(val coroutineScope: CoroutineScope)
