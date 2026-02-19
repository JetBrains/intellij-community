// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenProblemViewPanelCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = "${CMD_PREFIX}openProblemViewPanel"
    val LOG: Logger = logger<WaitForFinishedCodeAnalysis>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val markupModel = (FileEditorManager
      .getInstance(context.project)
      .selectedTextEditor!!
      .markupModel as EditorMarkupModelImpl)

    val uiController = readAction { markupModel.errorStripeRenderer!!.status.controller }

    withContext(Dispatchers.EDT) {
      uiController.toggleProblemsView()
    }
  }

  override fun getName(): String = "openProblemViewPanel"

}