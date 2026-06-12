// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.application.options.schemes.SchemeNameGenerator
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.io.awaitExit
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TerminalStarter
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.LocalTerminalTtyConnector
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.original
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Duration

internal fun TerminalTextBuffer.addModelListener(parentDisposable: Disposable, listener: TerminalModelListener) {
  addModelListener(listener)
  Disposer.register(parentDisposable) {
    removeModelListener(listener)
  }
}

@ApiStatus.Internal
@JvmField
val CONNECTOR_CLOSING_TIMEOUT: Duration = Duration.ofSeconds(3)

@ApiStatus.Internal
suspend fun TerminalStarter.closeConnectorAndStopEmulation() {
  // It is a cleanup activity - it shouldn't be canceled in the middle
  withContext(Dispatchers.IO + NonCancellable) {
    // No exception is expected there, but let's use `try` for extra safety to ensure that `requestEmulatorStop` is called in any case.
    try {
      ttyConnector.closeAndWaitFor(CONNECTOR_CLOSING_TIMEOUT)
    }
    finally {
      // Stop reading from the connector
      requestEmulatorStop()
    }
  }
}

/**
 * @return the exit code of the process if it was closed successfully, null otherwise.
 */
@ApiStatus.Internal
suspend fun TtyConnector.closeAndWaitFor(timeout: Duration): Int? {
  return withContext(Dispatchers.IO) {
    val localTtyConnector = original as? LocalTerminalTtyConnector
    if (localTtyConnector != null) {
      localTtyConnector.closeSafely()
    }
    else close()

    waitFor(timeout)
  }
}

/**
 * Waits for the [TtyConnector] closing until [timeout] elapses.
 * @return the exit code of the process if it was closed successfully, null otherwise.
 */
@ApiStatus.Internal
suspend fun TtyConnector.waitFor(timeout: Duration): Int? {
  if (!this.isConnected) {
    return waitFor() // should return the exit code of the process immediately
  }
  return withTimeoutOrNull(timeout) {
    val processTtyConnector = original as? ProcessTtyConnector
    if (processTtyConnector != null) {
      processTtyConnector.process.awaitExit()
    }
    else withContext(Dispatchers.IO) {
      waitFor()
    }
  }
}

/**
 * Waits for the TtyConnector closing and executes the [callback] asynchronously.
 */
@ApiStatus.Internal
fun TtyConnector.waitForAsync(timeout: Duration, callback: () -> Unit) {
  if (!this.isConnected) {
    callback()
    return
  }
  val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(this)
  if (processTtyConnector != null) {
    val onExit = processTtyConnector.process.onExit()
    terminalApplicationScope().launch(Dispatchers.IO) {
      try {
        withTimeout(timeout) {
          onExit.await() // the future will be canceled on timeout (not affecting the process itself)
        }
      }
      finally {
        callback()
      }
    }
  }
  else {
    val connector = this
    terminalApplicationScope().launch(Dispatchers.IO) {
      try {
        withTimeout(timeout) {
          connector.waitFor()
        }
      }
      finally {
        callback()
      }
    }
  }
}

internal fun TtyConnector.getDebugName(): @NonNls String {
  val processTtyConnector: ProcessTtyConnector? = ShellTerminalWidget.getProcessTtyConnector(this)
  if (processTtyConnector != null) {
    val commandLineText = processTtyConnector.commandLine?.joinToString(separator = " ")
    return processTtyConnector.process::class.java.simpleName + (commandLineText ?: "<no command line>")
  }
  return name
}

@ApiStatus.Internal
fun <T : Any> fireListenersAndLogAllExceptions(
  listeners: List<T>,
  logger: Logger,
  message: String,
  callListener: (T) -> Unit,
) {
  for (listener in listeners) {
    try {
      callListener(listener)
    }
    catch (e: Exception) {
      // Even log a cancellation exception because we do not expect it to be thrown from the listener
      PluginException.logPluginError(logger, message, e, listener.javaClass)
    }
  }
}

/**
 * Sets the shortcut for the given action ID.
 * If the provided shortcut is null, removes all shortcuts for the action.
 * Takes care of creating a new keymap if the current one cannot be modified.
 */
@ApiStatus.Internal
fun updateActionShortcut(actionId: String, value: KeyboardShortcut?) {
  val keymapToModify = getKeymapToModify() ?: return
  keymapToModify.removeAllActionShortcuts(actionId)
  if (value != null) {
    keymapToModify.addShortcut(actionId, value)
  }
}

private fun getKeymapToModify(): Keymap? {
  val keymapManager = KeymapManager.getInstance() as? KeymapManagerEx ?: return null

  val keymapToModify = keymapManager.activeKeymap
  return if (!keymapToModify.canModify()) {
    val allKeymaps = keymapManager.allKeymaps
    val name = SchemeNameGenerator.getUniqueName(
      KeyMapBundle.message("new.keymap.name", keymapToModify.presentableName)
    ) { newName: String ->
      allKeymaps.any { it.name == newName || it.presentableName == newName }
    }

    val newKeymap = keymapToModify.deriveKeymap(name)
    keymapManager.schemeManager.addScheme(newKeymap)
    keymapManager.activeKeymap = newKeymap
    newKeymap
  }
  else keymapToModify
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresReadLockAbsence(generateAssertion = false)
internal fun String?.toExistentNioDirectory(labelToLogOnFailure: String? = null): Path? {
  val directory = this ?: return null
  if (directory.isBlank()) {
    if (labelToLogOnFailure != null) {
      val type = if (directory.isEmpty()) "empty" else "blank"
      fileLogger().warn("$labelToLogOnFailure: $type directory")
    }
    return null
  }
  val path: Path
  try {
    path = Path.of(directory)
  }
  catch (e: InvalidPathException) {
    if (labelToLogOnFailure != null) {
      fileLogger().warn("$labelToLogOnFailure: invalid directory $directory", e)
    }
    return null
  }
  if (!path.isAbsolute) {
    if (labelToLogOnFailure != null) {
      fileLogger().warn("$labelToLogOnFailure: non-absolute directory: $directory")
    }
    return null
  }
  if (Files.isDirectory(path)) {
    return path
  }
  if (labelToLogOnFailure != null) {
    fileLogger().warn("$labelToLogOnFailure: non-existent directory: $directory")
  }
  return null
}
