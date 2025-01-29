// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindUtil
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.CopyOnWriteArrayList

internal class TerminalSearchController(
  private val project: Project,
  private val editor: EditorEx,
) {
  private val listeners = CopyOnWriteArrayList<TerminalSearchControllerListener>()
  private var searchSession: TerminalSearchSession? = null

  fun addListener(listener: TerminalSearchControllerListener) {
    listeners.add(listener)
  }

  @RequiresEdt
  fun startOrActivateSearchSession() {
    ThreadingAssertions.assertEventDispatchThread()
    val existingSession = searchSession
    if (existingSession == null) {
      startSession()
    }
    else {
      activateSession(existingSession)
    }
  }

  @RequiresEdt
  fun finishSearchSession() {
    ThreadingAssertions.assertEventDispatchThread()
    searchSession?.close()
  }

  fun hasActiveSession(): Boolean = searchSession != null

  private fun startSession() {
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
    // We only need to transfer the focus if the editor is still visible.
    // There can be several reasons for closing the search,
    // and in some cases the editor can be hidden (e.g., switching to the alternate buffer).
    if (editor.contentComponent.isShowing) {
      editor.contentComponent.requestFocusInWindow()
    }
  }

  private fun activateSession(session: TerminalSearchSession) {
    session.component.requestFocusInTheSearchFieldAndSelectContent(project)
    FindUtil.configureFindModel(false, editor, session.findModel, false)
    session.findModel.isGlobal = false
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
