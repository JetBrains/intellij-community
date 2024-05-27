// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.util.containers.nullize
import com.intellij.util.execution.ParametersListUtil
import com.jediterm.core.input.InputEvent.CTRL_MASK
import com.jediterm.core.input.KeyEvent.VK_HOME
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.exp.ShellCommandManager.Companion.LOG
import org.jetbrains.plugins.terminal.exp.ShellCommandManager.Companion.debug
import org.jetbrains.plugins.terminal.util.ShellType
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Prevents sending shell generator concurrently with other generator or other shell command.
 */
internal class ShellCommandExecutionManager(private val session: BlockTerminalSession, commandManager: ShellCommandManager) {

  private val listeners: CopyOnWriteArrayList<ShellCommandSentListener> = CopyOnWriteArrayList()

  /**
   * Used to synchronize access to several private fields of this object.
   */
  private val lock: Lock = Lock()

  /** Access to this field is synchronized using `lock` */
  private val scheduledGenerators: Queue<Generator> = LinkedList()

  /** Access to this field is synchronized using `lock` */
  private val scheduledKeyBindings: Queue<KeyBinding> = LinkedList()

  /** Access to this field is synchronized using `lock` */
  private var runningGenerator: Generator? = null

  /** Access to this field is synchronized using `lock` */
  private val scheduledCommands: Queue<String> = LinkedList()

  /** Access to this field is synchronized using `lock` */
  private var isInitialized: Boolean = false

  /** Access to this field is synchronized using `lock` */
  private var isCommandRunning: Boolean = false

  init {
    commandManager.addListener(object : ShellCommandListener {
      override fun initialized() {
        lock.withLock {
          isInitialized = true
        }
        processQueueIfReady()
      }

      override fun commandFinished(event: CommandFinishedEvent) {
        lock.withLock {
          if (!isCommandRunning) {
            LOG.warn("Received command_finished event, but command wasn't started")
          }
          isCommandRunning = false
        }
        processQueueIfReady()
      }

      override fun generatorFinished(event: GeneratorFinishedEvent) {
        lock.withLock { registrar ->
          if (runningGenerator == null) {
            LOG.warn("Received generator_finished event (request_id=${event.requestId}), but no running generator")
          }
          else {
            val runningGeneratorLocal = runningGenerator!!
            runningGenerator = null
            registrar.afterLock {
              if (event.requestId == runningGeneratorLocal.requestId) {
                val result = ShellCommandResult.create(event.output, event.exitCode)
                runningGeneratorLocal.deferred.complete(result)
              }
              else {
                val msg = "Received generator_finished event (request_id=${event.requestId}), but $runningGeneratorLocal was expected"
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

  private fun cancelGenerators(registrar: Lock.AfterLockActionRegistrar, incompatibleCondition: String) {
    runningGenerator?.let { runningGenerator ->
      registrar.afterLock {
        val msg = "Unexpectedly running $runningGenerator, but $incompatibleCondition"
        LOG.warn(msg)
        runningGenerator.deferred.completeExceptionally(IllegalStateException(msg))
      }
    }
    runningGenerator = null
    scheduledGenerators.drainToList().nullize()?.let { cancelledGenerators ->
      LOG.warn("Unexpected scheduled generators $cancelledGenerators, but $incompatibleCondition")
      registrar.afterLock {
        cancelledGenerators.forEach {
          it.deferred.cancel(CancellationException(
            "Unexpectedly scheduled generator, but $incompatibleCondition"))
        }
      }
    }
  }

  /**
   * If the command could not be executed right now, then we add it to the queue.
   */
  fun sendCommandToExecute(shellCommand: String) {
    lock.withLock {
      if (isCommandRunning) {
        LOG.info("Command '$shellCommand' is postponed until currently running command is finished")
      }
      if (!isInitialized) {
        LOG.info("Command '$shellCommand' is postponed until `initialized` event is received")
      }
      scheduledCommands.offer(shellCommand)
    }
    processQueueIfReady()
  }

  /**
   * Adds the KeyBinding to the queue to be executed when the terminal becomes free.
   * Guaranteed to execute only if the terminal is free.
   * If the terminal is executing user command, then queued Key Bindings could be lost.
   * If a new command starts after the Key Binding execution is queued, then queued Key Binding could be lost and not applied.
   */
  internal fun sendKeyBinding(keyBinding: KeyBinding) {
    lock.withLock {
      scheduledKeyBindings.offer(keyBinding)
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
  fun runGeneratorAsync(shellCommand: String): Deferred<ShellCommandResult> {
    val generator = Generator(shellCommand)
    lock.withLock {
      scheduledGenerators.offer(generator)
    }
    processQueueIfReady()
    return generator.deferred
  }

  /**
   * Should be called without [lock].
   *
   * Tries to process the queue of requests (commands, generators, keybindings).
   * Any command cancels all the generators.
   */
  private fun processQueueIfReady() {
    lock.withLock { registrar ->
      if (!isInitialized) {
        cancelGenerators(registrar, "not initialized yet")
        return@withLock // `initialized` event will resume queue processing
      }
      if (isCommandRunning) {
        cancelGenerators(registrar, "command is running")
        return@withLock // `commandFinished` event will resume queue processing
      }
      if (runningGenerator != null) {
        return@withLock // `generatorFinished` event will resume queue processing
      }

      scheduledKeyBindings.drainToList().forEach { scheduledInput ->
        session.terminalStarterFuture.thenAccept { terminalStarter ->
          terminalStarter?.sendBytes(scheduledInput.bytes, false)
        }
      }

      scheduledCommands.poll()?.let { command ->
        cancelGenerators(registrar, "user command is ready to execute")
        isCommandRunning = true
        doSendCommandToExecute(command, false)
        return@withLock // `commandFinished` event will resume queue processing
      }
      pollNextGeneratorToRun()?.let {
        runningGenerator = it
        doSendCommandToExecute(it.shellCommand(), true)
        // `generatorFinished` event will resume queue processing
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

  private fun doSendCommandToExecute(shellCommand: String, isGenerator: Boolean) {
    // in the IDE we use '\n' line separator, but Windows requires '\r\n'
    val adjustedCommand = shellCommand.replace("\n", System.lineSeparator())
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
      TerminalUtil.sendCommandToExecute(clearPrompt + adjustedCommand, starter)

      if (isGenerator) {
        fireGeneratorCommandSent(shellCommand)
      }
      else {
        fireUserCommandSent(shellCommand)
      }
    }
  }

  fun addListener(listener: ShellCommandSentListener, parentDisposable: Disposable = session) {
    TerminalUtil.addItem(listeners, listener, parentDisposable)
  }

  private fun fireUserCommandSent(userCommand: String) {
    for (listener in listeners) {
      listener.userCommandSent(userCommand)
    }
    debug { "User command sent: $userCommand" }
  }

  private fun fireGeneratorCommandSent(generatorCommand: String) {
    for (listener in listeners) {
      listener.generatorCommandSent(generatorCommand)
    }
    debug { "Generator command sent: $generatorCommand" }
  }

  /**
   * This is a shell command.
   * User does not type this in prompt.
   * It is the command created for Shell Integration
   * to gain some data from shell.
   * Usually, generators are implemented as shell functions reporting
   * back to IDE information wrapped in a custom OSC escape sequence.
   */
  private inner class Generator(private val shellCommand: String) {
    val requestId: Int = NEXT_REQUEST_ID.incrementAndGet()
    val deferred: CompletableDeferred<ShellCommandResult> = CompletableDeferred()

    fun shellCommand(): String {
      val escapedCommand = when (session.shellIntegration.shellType) {
        ShellType.POWERSHELL -> StringUtil.wrapWithDoubleQuote(escapePowerShellParameter(shellCommand))
        else -> ParametersListUtil.escape(shellCommand)
      }
      return "$GENERATOR_COMMAND $requestId $escapedCommand"
    }

    override fun toString(): String = "Generator(command=$shellCommand, requestId=$requestId)"
  }

  internal class KeyBinding(val bytes: ByteArray)

  /**
   * A wrapper around `synchronized` section with the ability to run actions after the section.
   */
  private class Lock {
    private val lock: Any = Any()

    fun withLock(block: (AfterLockActionRegistrar) -> Unit) {
      val afterLockBlocks: MutableList<() -> Unit> = ArrayList()
      try {
        synchronized(lock) {
          block(object : AfterLockActionRegistrar {
            override fun afterLock(block: () -> Unit) {
              afterLockBlocks.add(block)
            }
          })
        }
      }
      finally {
        afterLockBlocks.forEach { it() }
      }
    }

    interface AfterLockActionRegistrar {
      fun afterLock(block: () -> Unit)
    }
  }

  companion object {
    private val NEXT_REQUEST_ID = AtomicInteger(0)
    private const val GENERATOR_COMMAND = "__jetbrains_intellij_run_generator"

    @Suppress("SpellCheckingInspection")
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

    fun escapePowerShellParameter(parameter: String): String {
      return buildString(parameter.length) {
        for (ch in parameter) {
          append(pwshCharsToEscape[ch] ?: ch)
        }
      }
    }
  }
}

internal interface ShellCommandSentListener {
  /**
   * Called when a user command has been sent for execution in shell.
   * Might be called in the background or in the UI thread.
   *
   * Please note this call may happen prior to actual writing bytes to TTY.
   */
  fun userCommandSent(userCommand: String) {}

  /**
   * Called when a generator command has been sent for execution in shell.
   * Might be called in the background or in the UI thread.
   *
   * Please note this call may happen prior to actual writing bytes to TTY.
   */
  fun generatorCommandSent(generatorCommand: String) {}
}
