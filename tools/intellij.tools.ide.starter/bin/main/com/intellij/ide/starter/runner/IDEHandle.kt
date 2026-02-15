package com.intellij.ide.starter.runner

import com.intellij.ide.starter.process.exec.ProcessExecutor.Companion.killProcessGracefully
import com.intellij.ide.starter.utils.catchAll

interface IDEHandle {
  val id: String

  val isAlive: Boolean

  fun kill()
}

class IDEProcessHandle(private val process: ProcessHandle) : IDEHandle {
  override val id: String
    get() = process.pid().toString()

  override val isAlive: Boolean
    get() = process.isAlive

  override fun kill() {
    process.descendants().forEach { catchAll { killProcessGracefully(it) } }
    catchAll { killProcessGracefully(process) }
  }
}