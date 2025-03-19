// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindUtil
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.CopyOnWriteArrayList

internal class TerminalSearchController(private val project: Project) {
  private val listeners = CopyOnWriteArrayList<TerminalSearchControllerListener>()
  private var searchSession: TerminalSearchSession? = null

  fun addListener(listener: TerminalSearchControllerListener) {
    listeners.add(listener)
  }

  @RequiresEdt
  fun startOrActivateSearchSession(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val existingSession = searchSession
    if (existingSession == null) {
      startSession(editor)
    }
    else {
      existingSession.activate()
    }
  }

  @RequiresEdt
  fun finishSearchSession() {
    ThreadingAssertions.assertEventDispatchThread()
    searchSession?.close()
  }

  fun hasActiveSession(): Boolean = searchSession != null

  private fun startSession(editor: Editor) {
    val findModel = FindModel()
    findModel.copyFrom(FindManager.getInstance(project).findInFileModel)
    findModel.isWholeWordsOnly = false
    FindUtil.configureFindModel(false, editor, findModel, false)
    findModel.isGlobal = false
    val session = TerminalSearchSession(project, editor, findModel, closeCallback = this::onSearchClosed)
    searchSession = session
    listeners.forEach { it.searchSessionStarted(session) }
    session.component.requestFocusInTheSearchFieldAndSelectContent(project)
  }

  private fun onSearchClosed() {
    val session = searchSession ?: return
    listeners.forEach { it.searchSessionFinished(session)}
    searchSession = null
  }

  @RequiresEdt
  fun searchForward() {
    ThreadingAssertions.assertEventDispatchThread()
    searchSession?.searchForward()
  }

  @RequiresEdt
  fun searchBackward() {
    ThreadingAssertions.assertEventDispatchThread()
    searchSession?.searchBackward()
  }

  companion object {
    val KEY: DataKey<TerminalSearchController> = DataKey.create("TerminalSearchController")
  }
}

internal interface TerminalSearchControllerListener {
  fun searchSessionStarted(session: TerminalSearchSession)
  fun searchSessionFinished(session: TerminalSearchSession)
}
