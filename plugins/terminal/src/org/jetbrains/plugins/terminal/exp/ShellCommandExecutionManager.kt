// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.nullize
import com.intellij.util.execution.ParametersListUtil
import com.jediterm.core.input.InputEvent.CTRL_MASK
import com.jediterm.core.input.KeyEvent.VK_HOME
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.exp.ShellCommandManager.Companion.LOG
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Prevents sending shell generator concurrently with other generator or other shell command.
 */
internal class ShellCommandExecutionManager(private val session: BlockTerminalSession, commandManager: ShellCommandManager) {

  /**
   * Used to synchronize access to several private fields of this object.
   */
  private val lock: Lock = Lock()

  /** Access to this field is synchronized using `lock` */
  private val scheduledGenerators: Queue<Generator> = LinkedList()

  /** Access to this field is synchronized using `lock` */
  private var runningGenerator: Generator? = null

  /** Access to this field is synchronized using `lock` */
  private val scheduledCommands: Queue<String> = LinkedList()

  /** Access to this field is synchronized using `lock` */
  private var isInitialized: Boolean = false

  /** Access to this field is synchronized using `lock` */
  private var isCommandRunning: Boolean = false

  private val commandSentListeners: MutableList<(String) -> Unit> = CopyOnWriteArrayList()

  init {
    commandManager.addListener(object : ShellCommandListener {
      override fun initialized() {
        lock.withLock { withoutLock ->
          isInitialized = true
          cancelGenerators(withoutLock, "initialized")
        }
        processQueueIfReady()
      }

      override fun commandFinished(event: CommandFinishedEvent) {
        lock.withLock { withoutLock ->
          if (!isCommandRunning) {
            LOG.warn("Received command_finished event, but command wasn't started")
          }
          isCommandRunning = false
          cancelGenerators(withoutLock, "command_finished")
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

  private fun cancelGenerators(withoutLock: WithoutLockRegistrar, receivedEvent: String) {
    runningGenerator?.let { runningGenerator ->
      withoutLock {
        val msg = "Unexpectedly running $runningGenerator when $receivedEvent event received"
        LOG.warn(msg)
        runningGenerator.deferred.completeExceptionally(IllegalStateException(msg))
      }
    }
    runningGenerator = null
    scheduledGenerators.drainToList().nullize()?.let { cancelledGenerators ->
      LOG.warn("Unexpected scheduled generators $cancelledGenerators when $receivedEvent event received")
      withoutLock {
        cancelledGenerators.forEach {
          it.deferred.cancel(CancellationException(
            "Unexpectedly scheduled generator when $receivedEvent event received"))
        }
      }
    }
  }

  /**
   * If the command could not be executed right now, then we add it to the queue.
   */
  fun sendCommandToExecute(shellCommand: String) {
    // in the IDE we use '\n' line separator, but Windows requires '\r\n'
    val command = shellCommand.replace("\n", System.lineSeparator())
    lock.withLock {
      if (isCommandRunning) {
        LOG.info("Command '$command' is postponed until currently running command is finished")
      }
      if (!isInitialized) {
        LOG.info("Command '$command' is postponed until `initialized` event is received")
      }
      scheduledCommands.offer(command)
    }
    processQueueIfReady()
  }

  /**
   * This is similar to sendCommandToExecute with the difference in termination signal.
   * This sends "GENERATOR_FINISHED" instead of "Command finished" event.
   *
   * This does not execute command immediately, rather adds it to queue to be
   * executed when other commands\generators are finished and the shell is free.
   */
  fun runGeneratorAsync(generatorName: String, generatorParameters: List<String>): Deferred<String> {
    val generator = Generator(generatorName, generatorParameters)
    lock.withLock { withoutLock ->
      val cancelReason: String? = when {
        isCommandRunning -> "Generator shouldn't be scheduled when command is running"
        !isInitialized -> "Generator shouldn't be scheduled when session hasn't been initialized yet"
        else -> null
      }
      if (cancelReason != null) {
        withoutLock {
          generator.deferred.completeExceptionally(IllegalStateException(cancelReason))
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
      if (runningGenerator == null && !isCommandRunning && isInitialized) {
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

  /**
   * Polls all the elements of the queue and collects them to a new list.
   */
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

  /**
   * This is a shell command.
   * User does not type this in prompt.
   * It is the command created for Shell Integration
   * to gain some data from shell.
   * Usually, generators are implemented as shell functions reporting
   * back to IDE information wrapped in a custom OSC escape sequence.
   *
   * @see org.jetbrains.plugins.terminal.exp.completion.DataProviderCommand
   */
  private inner class Generator(val name: String, val parameters: List<String>) {
    val requestId: Int = NEXT_REQUEST_ID.incrementAndGet()
    val deferred: CompletableDeferred<String> = CompletableDeferred()

    fun shellCommand(): String {
      val joinedParams = when (session.shellIntegration.shellType) {
        ShellType.POWERSHELL -> parameters.joinToString(" ") { StringUtil.wrapWithDoubleQuote(escapePowerShellParameter(it)) }
        else -> ParametersListUtil.join(parameters)
      }
      return "$name $requestId $joinedParams"
    }

    override fun toString(): String = "Generator($name, parameters=$parameters, requestId=$requestId)"
  }

  /**
   * A wrapper for invoking code in synchronized section.
   * Allows executing a lambda under the lock.
   * Allows collecting the tasks to be executed after the lock is released.
   */
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

    private val pwshCharsToEscape: Map<Char, String> = mapOf(
      '`' to "``",
      '\"' to "`\"",
      '\u0000' to "`0",
      '\u0007' to "`a",
      '\u0008' to "`b",
      '\u000c' to "`f",
      '\n' to "`n",
      '\r' to "`r",
      '\t' to "`t",
      '\u000B' to "'v",
      '$' to "`$"
    )

    private fun escapePowerShellParameter(parameter: String): String {
      return buildString(parameter.length) {
        for (ch in parameter) {
          append(pwshCharsToEscape[ch] ?: ch)
        }
      }
    }
  }
}

private typealias WithoutLockRegistrar = (() -> Unit) -> Unit
