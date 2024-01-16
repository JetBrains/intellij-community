// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.nullize
import com.intellij.util.execution.ParametersListUtil
import com.jediterm.core.input.InputEvent.CTRL_MASK
import com.jediterm.core.input.KeyEvent.VK_HOME
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.exp.ShellCommandManager.Companion.LOG
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal class ShellCommandExecutionManager(private val session: BlockTerminalSession, commandManager: ShellCommandManager) {

  private val lock: Lock = Lock()

  // these fields are guarded by `lock`
  private val scheduledGenerators: Queue<Generator> = LinkedList()
  private var runningGenerator: Generator? = null
  private val scheduledCommands: Queue<String> = LinkedList()
  private var isCommandRunning: Boolean = false

  private val commandSentListeners: MutableList<(String) -> Unit> = CopyOnWriteArrayList()

  init {
    commandManager.addListener(object : ShellCommandListener {
      override fun commandFinished(command: String?, exitCode: Int, duration: Long?) {
        lock.withLock { withoutLock ->
          if (!isCommandRunning) {
            LOG.warn("Received command_finished event, but command wasn't started")
          }
          isCommandRunning = false
          if (runningGenerator != null) {
            val runningGeneratorLocal = runningGenerator!!
            runningGenerator = null
            withoutLock {
              val msg = "Unexpectedly running $runningGeneratorLocal when command_finished event received"
              LOG.warn(msg)
              runningGeneratorLocal.deferred.completeExceptionally(IllegalStateException(msg))
            }
          }
          scheduledGenerators.drainToList().nullize()?.let { cancelledGenerators ->
            LOG.warn("Unexpected scheduled generators $cancelledGenerators when command_finished event received")
            withoutLock {
              cancelledGenerators.forEach {
                it.deferred.cancel(CancellationException(
                  "Unexpected scheduled generators when command_finished event received"))
              }
            }
          }
        }
        processQueueIfReady()
      }

      override fun generatorFinished(requestId: Int, result: String) {
        lock.withLock { withoutLock ->
          if (runningGenerator == null) {
            LOG.warn("Received generator_finished event (request_id=${requestId}), but no running generator")
          }
          else {
            val runningGeneratorLocal = runningGenerator!!
            runningGenerator = null
            withoutLock {
              if (requestId == runningGeneratorLocal.requestId) {
                runningGeneratorLocal.deferred.complete(result)
              }
              else {
                val msg = "Received generator_finished event (request_id=${requestId}), but $runningGeneratorLocal was expected"
                LOG.warn(msg)
                runningGeneratorLocal.deferred.completeExceptionally(IllegalStateException(msg))
              }
            }
          }
        }
        processQueueIfReady()
      }
    }, session)
  }

  fun sendCommandToExecute(shellCommand: String) {
    // in the IDE we use '\n' line separator, but Windows requires '\r\n'
    val command = shellCommand.replace("\n", System.lineSeparator())
    lock.withLock {
      if (isCommandRunning) {
        LOG.warn("Command '$command' execution is postponed until currently running command is finished")
      }
      scheduledCommands.offer(command)
    }
    processQueueIfReady()
  }

  fun runGeneratorAsync(generatorName: String, generatorParameters: List<String>): CompletableDeferred<String> {
    val generator = Generator(generatorName, generatorParameters)
    lock.withLock { withoutLock ->
      if (isCommandRunning) {
        withoutLock {
          generator.deferred.completeExceptionally(IllegalStateException(
            "Generator shouldn't be scheduled when command is running"
          ))
        }
      }
      scheduledGenerators.offer(generator)
    }
    processQueueIfReady()
    return generator.deferred
  }

  @TestOnly
  fun addCommandSentListener(disposable: Disposable, listener: (String) -> Unit) {
    TerminalUtil.addItem(commandSentListeners, listener, disposable)
  }

  // should be called without `lock`
  private fun processQueueIfReady() {
    lock.withLock { withoutLock ->
      if (runningGenerator == null && !isCommandRunning) {
        scheduledCommands.poll()?.let { command ->
          // cancel previously scheduled generators, because user command is already ready
          scheduledGenerators.drainToList().nullize()?.let { cancelledGenerators ->
            withoutLock {
              cancelledGenerators.forEach { it.deferred.cancel(CancellationException("Generator cancelled because of executing command")) }
            }
          }
          isCommandRunning = true
          doSendCommandToExecute(command)
          return@withLock
        }
        pollNextGeneratorToRun()?.let {
          runningGenerator = it
          doSendCommandToExecute(it.shellCommand())
        }
      }
    }
  }

  private fun pollNextGeneratorToRun(): Generator? {
    var generator: Generator?
    do {
      generator = scheduledGenerators.poll()
    }
    while (generator != null && !generator.deferred.isActive)
    return generator
  }

  private fun <T> Queue<T>.drainToList(): List<T> = ArrayList<T>(size).also {
    while (isNotEmpty()) {
      it.add(poll()!!)
    }
  }

  private fun doSendCommandToExecute(shellCommand: String) {
    commandSentListeners.forEach { it(shellCommand) }
    session.terminalStarterFuture.thenAccept { starter ->
      starter ?: return@thenAccept
      val clearPrompt: String = when (session.shellIntegration.shellType) {
        ShellType.POWERSHELL -> {
          // Simulate pressing Ctrl+Home to delete all the characters from
          // the cursor's position to the beginning of a line.
          starter.terminal.getCodeForKey(VK_HOME, CTRL_MASK)!!.toString(Charsets.UTF_8)
        }
        // Simulate pressing Ctrl+U in the terminal to clear all typings in the prompt (IDEA-337692)
        else -> "\u0015"
      }
      TerminalUtil.sendCommandToExecute(clearPrompt + shellCommand, starter)
    }
  }

  private inner class Generator(val name: String, val parameters: List<String>) {
    val requestId: Int = NEXT_REQUEST_ID.incrementAndGet()
    val deferred: CompletableDeferred<String> = CompletableDeferred()

    fun shellCommand(): String {
      val joinedParams = when (session.shellIntegration.shellType) {
        ShellType.POWERSHELL -> parameters.joinToString(" ") { StringUtil.wrapWithDoubleQuote(it) }
        else -> ParametersListUtil.join(parameters)
      }
      return "$name $requestId $joinedParams"
    }

    override fun toString(): String = "Generator($name, parameters=$parameters, requestId=$requestId)"
  }

  private class Lock {
    private val lock: Any = Any()

    fun withLock(block: (WithoutLockRegistrar) -> Unit) {
      val withoutLockBlocks: MutableList<() -> Unit> = ArrayList()
      try {
        synchronized(lock) {
          block {
            withoutLockBlocks.add(it)
          }
        }
      }
      finally {
        withoutLockBlocks.forEach { it() }
      }
    }
  }

  companion object {
    private val NEXT_REQUEST_ID = AtomicInteger(0)
  }
}

private typealias WithoutLockRegistrar = (() -> Unit) -> Unit