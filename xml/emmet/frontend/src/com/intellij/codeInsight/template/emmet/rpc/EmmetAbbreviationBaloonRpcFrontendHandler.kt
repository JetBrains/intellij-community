// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.rpc

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.LightColors
import com.intellij.ui.TextFieldWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.util.function.Consumer

/**
 * This is a proxy between the legacy code and the rpc approach
 */
class EmmetAbbreviationBaloonRpcFrontendHandler(project: Project, val showEvent: ShowAbbreviationBaloonUiEvent) {
  private val rpcScope = EmmetFrontendRpcService.scope(project).childScope("Emmet abbreviation baloon rpc scope")

  private val cleanRpcScope: CoroutineScope
    get(): CoroutineScope = rpcScope.also {
      it.coroutineContext.cancelChildren()
    }

  fun enter(abbreviation: String, callback: Runnable) {
    cleanRpcScope.launch {
        EmmetAbbreviationBaloonRpc.instance().enter(showEvent.transactionId, showEvent.editorId, abbreviation)
        invokeLater {
          callback.run()
        }
      }
  }

  fun validateTemplateKey(
    field: TextFieldWithHistory,
    balloon: Balloon?,
    handler: Consumer<Boolean>,
  ) {
    val abbreviation = field.text
    cleanRpcScope.launch {
      val isCorrect = EmmetAbbreviationBaloonRpc.instance().isValidTemplateKey(
        showEvent.transactionId, showEvent.editorId, abbreviation)

      invokeLater {
        if (!abbreviation.equals(field.text)) {
          // text changed while we were checking it
          validateTemplateKey(field, balloon, handler)
          return@invokeLater
        }
        field.textEditor.setBackground(if (isCorrect) LightColors.SLIGHTLY_GREEN else LightColors.RED)
        if (balloon != null && !balloon.isDisposed()) {
          balloon.revalidate()
        }
        handler.accept(isCorrect)
      }
    }
  }

  fun cancel(callback: Runnable) {
    cleanRpcScope.launch {
      invokeLater {
        callback.run()
      }
      EmmetAbbreviationBaloonRpc.instance().cancel(showEvent.transactionId, showEvent.editorId)
      rpcScope.cancel()
    }
  }

  /**
   * This method probably could be removed, but it is more about the business logic and i don't want to change it now
   */
  fun isValid(handler: Consumer<Boolean>) {
    cleanRpcScope.launch {
      val isvalid = EmmetAbbreviationBaloonRpc.instance().isValid(showEvent.transactionId, showEvent.editorId)
      invokeLater {
        handler.accept(isvalid)
      }
    }
  }
}
