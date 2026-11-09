// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonCommonInterpreterFacts")

package com.jetbrains.python.fixture

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.testFramework.dispatchAllEventsInIdeEventQueue
import com.intellij.util.ui.EDT
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Writes [paths] as the paths that the user added to [interpreter], and waits until its roots are committed, see
 * [PythonInterpreterDetails.writeAddedPaths]. The roots are committed in a write action on the EDT. On the EDT, the wait
 * dispatches the EDT events without the write-intent lock, so that write action can run.
 */
@TestOnly
@OptIn(DelicateCoroutinesApi::class)
fun writeAddedPathsForTests(project: Project, interpreter: PythonInterpreter, paths: List<Path>) {
  val details = PythonInterpreterDetails.getInstance(project)
  if (!EDT.isCurrentThreadEdt()) {
    runBlocking { details.writeAddedPaths(interpreter, paths) }
    return
  }
  val write = GlobalScope.async(Dispatchers.Default) { details.writeAddedPaths(interpreter, paths) }
  val deadline = TimeSource.Monotonic.markNow() + 1.minutes
  try {
    while (!write.isCompleted) {
      check(deadline.hasNotPassedNow()) { "The write of the added paths of $interpreter did not end in 1 minute" }
      dispatchAllEventsInIdeEventQueue()
    }
    // The write is complete, so this rethrows its failure at once and does not block.
    runBlocking { write.await() }
  }
  finally {
    write.cancel()
  }
}
