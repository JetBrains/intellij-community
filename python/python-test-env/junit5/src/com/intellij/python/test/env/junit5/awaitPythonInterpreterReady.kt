// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.testFramework.IndexingTestUtil
import com.jetbrains.python.sdk.PythonSdkUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Waits until the update of [interpreter] ends, then until [project] is indexed. A test calls it before it highlights
 * code.
 *
 * Setting an interpreter schedules an update of its SDK, which generates binary skeletons in the background. While any
 * skeletons are generated, the "unresolved reference" inspection reports nothing in any project, see
 * `PyUnresolvedReferenceDefaultInspectionExtension`. The update also changes the roots, so the index follows it.
 *
 * Only the update of [interpreter] is awaited. A skeleton generation that another test left behind must not block
 * this one.
 */
suspend fun awaitPythonInterpreterReady(interpreter: PythonInterpreter, project: Project, timeout: Duration = 5.minutes) {
  withTimeout(timeout) {
    awaitSdkUpdate(interpreter)
  }
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
}

/**
 * Waits up to [timeout] until the update of [interpreter] ends. A fixture calls it before it removes the interpreter:
 * the removal kills a skeleton generator that still runs, and the kill can leave a generator process behind.
 */
internal suspend fun awaitPythonInterpreterUpdateBeforeRemoval(interpreter: PythonInterpreter, timeout: Duration = 5.minutes) {
  withTimeoutOrNull(timeout) {
    awaitSdkUpdate(interpreter)
  }
}

private suspend fun awaitSdkUpdate(interpreter: PythonInterpreter) {
  @Suppress("DEPRECATION") // The SDK updater still takes an SDK.
  val sdk = interpreter.getSdkAPI()
  while (PythonSdkUpdater.isUpdateScheduled(sdk)) {
    delay(100.milliseconds)
  }
}
