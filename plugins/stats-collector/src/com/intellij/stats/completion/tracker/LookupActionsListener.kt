// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion.tracker

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException

private val LOG = logger<LookupActionsListener>()

internal class LookupActionsListener private constructor(): AnActionListener {
  companion object {
    private var subscribed = false
    private val instance = LookupActionsListener()

    fun getInstance(): LookupActionsListener {
      if (!subscribed) {
        ApplicationManager.getApplication().messageBus.connect().subscribe(AnActionListener.TOPIC, instance)
        subscribed = true
      }
      return instance
    }
  }

  private val down by lazy { ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN) }
  private val up by lazy { ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP) }
  private val backspace by lazy { ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE) }

  var listener: CompletionPopupListener = CompletionPopupListener.DISABLED

  override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
    LOG.runAndLogException {
      when (action) {
        down -> listener.downPressed()
        up -> listener.upPressed()
        backspace -> listener.afterBackspacePressed()
      }
    }
  }

  override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
    LOG.runAndLogException {
      when (action) {
        down -> listener.beforeDownPressed()
        up -> listener.beforeUpPressed()
        backspace -> listener.beforeBackspacePressed()
      }
    }
  }

  override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
    LOG.runAndLogException {
      listener.beforeCharTyped(c)
    }
  }
}