// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonInterpreterFactsForTests")

package com.jetbrains.python.tools.sdkTools

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Writes [paths] as the paths that the user added to [interpreter], and waits until its roots are committed, see
 * [PythonInterpreterDetails.writeAddedPaths]. For a test that cannot suspend, such as a Java fixture.
 */
@TestOnly
fun writeAddedPathsForTests(project: Project, interpreter: PythonInterpreter, paths: List<Path>) {
  runSuspendingForTests {
    PythonInterpreterDetails.getInstance(project).writeAddedPaths(interpreter, paths)
    PythonInterpreterRoots.getInstance(project).awaitCommittedForTests()
  }
}

/**
 * Writes [paths] as the paths that the user excluded from [interpreter], and waits until its roots are committed, see
 * [PythonInterpreterDetails.writeExcludedPaths]. For a test that cannot suspend, such as a Java fixture.
 */
@TestOnly
fun writeExcludedPathsForTests(project: Project, interpreter: PythonInterpreter, paths: Set<Path>) {
  runSuspendingForTests {
    PythonInterpreterDetails.getInstance(project).writeExcludedPaths(interpreter, paths)
    PythonInterpreterRoots.getInstance(project).awaitCommittedForTests()
  }
}

/**
 * Asks for a new derive of the roots of [interpreter] and waits until they are committed, see
 * [PythonInterpreterRoots.requestDerive]. For a test that cannot suspend, such as a Java fixture.
 */
@TestOnly
fun deriveRootsForTests(project: Project, interpreter: PythonInterpreter) {
  runSuspendingForTests {
    val roots = PythonInterpreterRoots.getInstance(project)
    roots.requestDerive(interpreter)
    roots.awaitCommittedForTests()
  }
}

/**
 * Runs [block] on a background thread and waits for its result, for a test that cannot suspend, such as a Java fixture.
 * On the EDT, the wait dispatches the EDT events without the write-intent lock, so a write action of [block] can run.
 * A wait for the roots needs this: the writer commits them in a write action on the EDT, see
 * [PythonInterpreterRoots.requestDerive].
 */
@TestOnly
@OptIn(DelicateCoroutinesApi::class)
private fun <T> runSuspendingForTests(block: suspend CoroutineScope.() -> T): T {
  val job = GlobalScope.async(Dispatchers.Default, block = block)
  try {
    return PlatformTestUtil.waitForFuture(job.asCompletableFuture())
  }
  finally {
    job.cancel()
  }
}
