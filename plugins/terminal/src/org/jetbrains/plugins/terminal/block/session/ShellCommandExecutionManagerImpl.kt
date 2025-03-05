// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.nullize
import com.intellij.util.execution.ParametersListUtil
import com.jediterm.core.input.InputEvent.CTRL_MASK
import com.jediterm.core.input.KeyEvent
import com.jediterm.core.input.KeyEvent.VK_HOME
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.TerminalStarter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.block.session.ShellCommandManager.Companion.debug
import org.jetbrains.plugins.terminal.block.util.ActionCoordinator
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector
import org.jetbrains.plugins.terminal.fus.TimeSpanType
import org.jetbrains.plugins.terminal.util.ShellIntegration
import org.jetbrains.plugins.terminal.util.ShellType
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Prevents sending shell generator concurrently with other generator or other shell command.
 */
internal class ShellCommandExecutionManagerImpl(
  private val session: BlockTerminalSession,
  commandManager: ShellCommandManager,
  private val shellIntegration: ShellIntegration,
  private val terminal: Terminal,
  private val parentDisposable: Disposable
) : ShellCommandExecutionManager {

  private val listeners: EventDispatcher<ShellCommandSentListener>  = EventDispatcher.create(ShellCommandSentListener::class.java)

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

  /**
   * Marks if this executor knows about some running command in the shell session.
   * The commands could be executed from outside the execution manager (e.g., shell-queued commands).
   *
   * Access to this field is synchronized using `lock`
   */
  private var isCommandRunning: Boolean = false

  /**
   * Marks if this executor has sent the command for execution and expects it to finish.
   *
   * Access to this field is synchronized using `lock`
   */
  private var isCommandSent: Boolean = false

  private val metricCommandSubmitToVisuallyStarted = createCommandMetric(shellIntegration.shellType, TimeSpanType.FROM_COMMAND_SUBMIT_TO_VISUALLY_STARTED)
  private val metricCommandSubmitToActuallyStarted = createCommandMetric(shellIntegration.shellType, TimeSpanType.FROM_COMMAND_SUBMIT_TO_ACTUALLY_STARTED)

  init {
    commandManager.addListener(object : ShellCommandListener {
      override fun initialized() {
        lock.withLock {
          isInitialized = true
        }
        processQueueIfReady()
      }

      override fun commandStarted(command: String) {
        metricCommandSubmitToActuallyStarted.finished(command)
        lock.withLock {
          if (isCommandRunning) {
            LOG.warn("Received command_started event, but previous command wasn't finished")
          }
          isCommandRunning = true
        }
        processQueueIfReady()
      }

      override fun commandFinished(event: CommandFinishedEvent) {
        lock.withLock {
          if (!isCommandRunning) {
            LOG.warn("Received command_finished event, but command wasn't started")
          }
          isCommandSent = false
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
    }, parentDisposable)
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
  override fun sendCommandToExecute(shellCommand: String) {
    metricCommandSubmitToVisuallyStarted.started(shellCommand, TimeSource.Monotonic.markNow())
    metricCommandSubmitToActuallyStarted.started(shellCommand, TimeSource.Monotonic.markNow())
    lock.withLock { afterLock ->
      if (isCommandSent || isCommandRunning) {
        LOG.info("Command '$shellCommand' is postponed until currently running command is finished")
      }
      if (!isInitialized) {
        LOG.info("Command '$shellCommand' is postponed until `initialized` event is received")
      }
      scheduledCommands.offer(shellCommand)
      afterLock.afterLock { fireUserCommandSubmitted(shellCommand) }
    }
    processQueueIfReady()
  }

  /**
   * Adds the KeyBinding to the queue to be executed when the terminal becomes free.
   * Guaranteed to execute only if the terminal is free.
   * If the terminal is executing user command, then queued Key Bindings could be lost.
   * If a new command starts after the Key Binding execution is queued, then queued Key Binding could be lost and not applied.
   */
  override fun sendKeyBinding(keyBinding: KeyBinding) {
    lock.withLock {
      scheduledKeyBindings.offer(keyBinding)
    }
    processQueueIfReady()
  }

  /**
   * This is similar to [sendCommandToExecute] with the difference in finishing
   * event (`generator_finished` instead of `command_finished`).
   *
   * This does not execute the command immediately, rather adds it to the queue to be
   * executed when other commands\generators are finished and the shell is free.
   */
  override fun runGeneratorAsync(shellCommand: String): Deferred<ShellCommandResult> {
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
      if (isCommandSent || isCommandRunning) {
        cancelGenerators(registrar, "command is running")
        return@withLock // `commandFinished` event will resume queue processing
      }
      if (runningGenerator != null) {
        return@withLock // `generatorFinished` event will resume queue processing
      }

      val keyBindings = scheduledKeyBindings.drainToList()
      if (keyBindings.isNotEmpty()) {
        keyBindings.forEach { keyBinding ->
          session.terminalOutputStream.sendBytes(keyBinding.bytes, false)
        }
        if (shellIntegration.shellType == ShellType.BASH ) { // reset prompt state to trigger all shell events.
          val clearPrompt: String = createClearPromptShortcut(terminal)
          val enterCode = String(terminal.getCodeForKey(KeyEvent.VK_ENTER, 0), StandardCharsets.UTF_8)
          session.terminalOutputStream.sendString(clearPrompt + enterCode, false)
        }
      }

      scheduledCommands.poll()?.let { command ->
        cancelGenerators(registrar, "user command is ready to execute")
        isCommandSent = true
        doSendCommandToExecute(command, false, registrar)
        return@withLock // `commandFinished` event will resume queue processing
      }
      pollNextGeneratorToRun()?.let {
        runningGenerator = it
        doSendCommandToExecute(it.shellCommand(), true, registrar)
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

  private fun doSendCommandToExecute(shellCommand: String, isGenerator: Boolean, registrar: Lock.AfterLockActionRegistrar) {
    session.terminalStarterFuture.thenAccept { starter ->
      starter ?: return@thenAccept
      doSendCommandToExecute(starter, shellCommand)
      registrar.afterLock {
        if (isGenerator) {
          fireGeneratorCommandSent(shellCommand)
        }
        else {
          metricCommandSubmitToVisuallyStarted.finished(shellCommand)
          fireUserCommandSent(shellCommand)
        }
      }
    }
  }

  private fun doSendCommandToExecute(starter: TerminalStarter, shellCommand: String) {
    var adjustedCommand = shellCommand
    val enterCode = String(terminal.getCodeForKey(KeyEvent.VK_ENTER, 0), StandardCharsets.UTF_8)
    if (session.model.isBracketedPasteMode && (adjustedCommand.contains("\n") || adjustedCommand.contains(System.lineSeparator()))) {
      adjustedCommand = bracketed(adjustedCommand)
    }
    else {
      // in the IDE we use '\n' line separator, but Windows requires '\r\n'
      adjustedCommand = adjustedCommand.replace("\n", enterCode)
    }
    val clearPrompt = createClearPromptShortcut(terminal)
    TerminalUtil.sendCommandToExecute(clearPrompt + adjustedCommand, starter)
  }

  override fun addListener(listener: ShellCommandSentListener) {
    listeners.addListener(listener, parentDisposable)
  }

  override fun addListener(listener: ShellCommandSentListener, parentDisposable: Disposable) {
    listeners.addListener(listener, parentDisposable)
  }

  private fun fireUserCommandSent(userCommand: String) {
    listeners.multicaster.userCommandSent(userCommand)
    debug { "User command sent: $userCommand" }
  }

  private fun fireUserCommandSubmitted(userCommand: String) {
    listeners.multicaster.userCommandSubmitted(userCommand)
    debug { "User command submitted: $userCommand" }
  }

  private fun fireGeneratorCommandSent(generatorCommand: String) {
    listeners.multicaster.generatorCommandSent(generatorCommand)
    debug { "Generator command sent: $generatorCommand" }
  }

  private fun createClearPromptShortcut(terminal: Terminal): String {
    return  when (shellIntegration.shellType) {
      ShellType.POWERSHELL -> {
        // TODO SystemInfo will not work for SSH and WSL sessions.
        when {
          SystemInfo.isUnix -> {
            SHORTCUT_CTRL_U
          }
          else -> {
            // Simulate pressing Ctrl+Home to delete all the characters from
            // the cursor's position to the beginning of a line.
            terminal.getCodeForKey(VK_HOME, CTRL_MASK)!!.toString(Charsets.UTF_8)
          }
        }
      }
      // Simulate pressing Ctrl+U in the terminal to clear all typings in the prompt (IDEA-337692)
      else -> SHORTCUT_CTRL_U
    }
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
      val escapedCommand = when (shellIntegration.shellType) {
        ShellType.POWERSHELL -> StringUtil.wrapWithDoubleQuote(escapePowerShellParameter(shellCommand))
        else -> ParametersListUtil.escape(shellCommand)
      }
      return "$GENERATOR_COMMAND $requestId $escapedCommand"
    }

    override fun toString(): String = "Generator(command=$shellCommand, requestId=$requestId)"
  }

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
    private val LOG = logger<ShellCommandExecutionManagerImpl>()
    private val NEXT_REQUEST_ID = AtomicInteger(0)
    private const val GENERATOR_COMMAND = "__jetbrains_intellij_run_generator"
    private const val SHORTCUT_CTRL_U = "\u0015"

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

    private fun bracketed(command: String): String {
      return "\u001b[200~$command\u001b[201~"
    }

    private fun createCommandMetric(shellType: ShellType, type: TimeSpanType): ActionCoordinator<String, TimeMark> = ActionCoordinator<String, TimeMark>(
      onActionComplete = { command, startTime ->
        TerminalUsageTriggerCollector.logBlockTerminalTimeSpanFinished(
          null,
          shellType,
          type,
          startTime.elapsedNow()
        )
      },
      onActionDiscarded = { command, startTime ->
        TerminalUsageTriggerCollector.logBlockTerminalTimeSpanFinished(
          null,
          shellType,
          type,
          kotlin.time.Duration.INFINITE
        )
      },
      onActionUnknown = { command ->
        thisLogger().warn("Mismatched command $command")
      }
    )
  }
}
