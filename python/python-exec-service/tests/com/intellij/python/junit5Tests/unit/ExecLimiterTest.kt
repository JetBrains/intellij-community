// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.execution.Platform
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.systemOs
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ConcurrentProcessWeight
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@TestApplication
class ExecLimiterTest {
  @Test
  fun testLimit(@TempDir dir: Path): Unit = timeoutRunBlocking(1.minutes) {


    val (shell, arg) = localEel.exec.getShell()
    val sleepCmd = when (localEel.systemOs().platform) {
      Platform.WINDOWS -> "pause"
      // sleep(1) can't be used as it creates a separate process which can't be killed as ijent doesn't kill the process group
      // cat, from the other hand, stops as soon as its stdin gets closed, so killing sh is enough
      Platform.UNIX -> "cat"
    }
    val execService = ExecService()

    val weight = ConcurrentProcessWeight.HEAVY
    val limit = Registry.intValue("python.execService.limit.heavy")

    val workersCount = limit * 10
    val lock = Semaphore(permits = workersCount, acquiredPermits = workersCount)
    repeat(workersCount) { n ->
      launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        lock.release()
        val file = dir.resolve("$n.txt")
        execService.execGetStdout(
          binary = shell.asNioPath(),
          args = Args(arg, "echo 1 > $file && $sleepCmd"),
          options = ExecOptions(weight = weight)).orThrow()
      }
    }
    repeat(workersCount) {
      lock.acquire()
    }
    delay(500.milliseconds)
    Assertions.assertEquals(limit, dir.listDirectoryEntries().size, "No more than $limit processes must run at the same time")
    coroutineContext.job.cancelChildren()
  }
}