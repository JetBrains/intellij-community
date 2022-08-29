package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import java.util.*

class TestWatcherActions {
  private val _onFailureActions: MutableList<(IDETestContext) -> Unit> = Collections.synchronizedList(mutableListOf())
  val onFailureActions: List<(IDETestContext) -> Unit>
    get() = synchronized(_onFailureActions) { _onFailureActions.toList() }

  private val _onFinishedActions: MutableList<(IDETestContext) -> Unit> = Collections.synchronizedList(mutableListOf())
  val onFinishedActions: List<(IDETestContext) -> Unit>
    get() = synchronized(_onFinishedActions) { _onFinishedActions.toList() }

  fun addOnFailureAction(action: (IDETestContext) -> Unit): TestWatcherActions {
    synchronized(_onFailureActions) { _onFailureActions.add(action) }
    return this
  }

  fun addOnFinishedAction(action: (IDETestContext) -> Unit): TestWatcherActions {
    synchronized(_onFinishedActions) { _onFinishedActions.add(action) }
    return this
  }
}