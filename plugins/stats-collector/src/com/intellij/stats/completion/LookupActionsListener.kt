// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger

class LookupActionsListener : AnActionListener.Adapter() {
  private companion object {
    val LOG = Logger.getInstance(LookupActionsListener::class.java)
  }

  private val down = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
  private val up = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
  private val backspace = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE)

  var listener: CompletionPopupListener = CompletionPopupListener.Adapter()

  private fun logThrowables(block: () -> Unit) {
    try {
      block()
    }
    catch (e: Throwable) {
      logIfNotControlFlow(e)
    }
  }

  private fun logIfNotControlFlow(e: Throwable) {
    if (e is ControlFlowException) {
      throw e
    }
    else {
      LOG.error(e)
    }
  }

  override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent?) {
    logThrowables {
      when (action) {
        down -> listener.downPressed()
        up -> listener.upPressed()
        backspace -> listener.afterBackspacePressed()
      }
    }
  }

  override fun beforeActionPerformed(action: AnAction, dataContext: DataContext?, event: AnActionEvent?) {
    logThrowables {
      when (action) {
        down -> listener.beforeDownPressed()
        up -> listener.beforeUpPressed()
        backspace -> listener.beforeBackspacePressed()
      }
    }
  }

  override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
    logThrowables {
      listener.beforeCharTyped(c)
    }
  }
}