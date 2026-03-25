package com.intellij.ide.starter.runner

import com.intellij.ide.starter.process.ProcessKiller
import com.intellij.ide.starter.utils.catchAll
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.minutes

interface IDEHandle {
  val id: String

  val isAlive: Boolean

  fun kill()
}

class IDEProcessHandle(private val process: Process) : IDEHandle {
  override val id: String
    get() = process.pid().toString()

  override val isAlive: Boolean
    get() = process.isAlive

  override fun kill() {
    catchAll {
      @Suppress("TestOnlyProblems")
      timeoutRunBlocking(context = Dispatchers.IO, timeout = 3.minutes) { ProcessKiller.killProcess(process) }
    }
  }
}
