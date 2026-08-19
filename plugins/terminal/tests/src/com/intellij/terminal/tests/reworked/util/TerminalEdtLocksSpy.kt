// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.util.ui.EDT
import java.util.Collections

internal class TerminalEdtLocksSpy(parentDisposable: Disposable) {

  private val app = ApplicationManagerEx.getApplicationEx()
  private val usages = Collections.synchronizedList(mutableListOf<LockUsage>())

  init {
    app.addWriteActionListener(object : WriteActionListener {
      override fun beforeWriteActionStart(action: Class<*>) {
        record(LockKind.WRITE)
      }
    }, parentDisposable)
    app.addWriteIntentReadActionListener(object : WriteIntentReadActionListener {
      override fun beforeWriteIntentReadActionStart(action: Class<*>) {
        record(LockKind.WRITE_INTENT)
      }
    }, parentDisposable)
  }

  private fun record(kind: LockKind) {
    if (!EDT.isCurrentThreadEdt()) return

    val stack = Throwable().stackTrace
    val signature = culprit(stack) ?: return

    usages.add(LockUsage(kind, signature, stack.joinToString("\n") { "\tat $it" }))
  }

  private fun culprit(stack: Array<StackTraceElement>): String? {
    return stack.asSequence()
      .firstOrNull { el -> isTerminalFrame(el.className) }
      ?.let { "${it.className}#${it.methodName}" }
  }

  private fun isTerminalFrame(className: String): Boolean {
    return TERMINAL_PREFIXES.any { className.startsWith(it) } &&
           TERMINAL_TEST_PREFIXES.none { className.startsWith(it) }
  }

  fun lockUsages(kind: LockKind): List<LockUsage> = synchronized(usages) {
    usages.filter { it.kind == kind }
  }

  companion object {
    private val TERMINAL_PREFIXES = listOf(
      "com.intellij.terminal.",
      "org.jetbrains.plugins.terminal.",
    )

    private val TERMINAL_TEST_PREFIXES = listOf("com.intellij.terminal.tests.")
  }
}

internal enum class LockKind { WRITE, WRITE_INTENT }

internal data class LockUsage(
  val kind: LockKind,
  val signature: String,
  val stack: String,
) {
  override fun toString(): String {
    return "$kind lock was taken by $signature. Stacktrace:\n$stack"
  }
}