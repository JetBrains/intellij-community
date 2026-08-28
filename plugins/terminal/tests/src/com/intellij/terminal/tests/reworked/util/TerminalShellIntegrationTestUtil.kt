// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

/** Escape (0x1B): introduces CSI/OSC control sequences. */
internal val ESC: String = Char(0x1B).toString()

/** Bell (0x07): rings the terminal bell and also terminates OSC strings. */
internal val BEL: String = Char(0x07).toString()

/**
 * Builds a JetBrains shell-integration OSC sequence (`ESC ] 1341 ; <payload> BEL`), the same one the
 * bundled shell-integration scripts emit and
 * [com.intellij.terminal.frontend.session.TerminalShellIntegrationController] parses.
 */
internal fun shellIntegrationOsc(payload: String): String = "${ESC}]1341;$payload${BEL}"

/** Hex-encodes a shell-integration parameter value, matching the scripts' `__jetbrains_intellij_encode`. */
internal fun String.encodeShellIntegrationValue(): String =
  toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

/**
 * The `initialized` command that enables the shell integration. Every positional command needs it first:
 * the frontend creates the shell integration only when the session state reports it enabled.
 */
internal fun shellIntegrationInitializedOsc(workingDirectory: String): String =
  shellIntegrationOsc("initialized;current_directory=${workingDirectory.encodeShellIntegrationValue()}")

/** The `prompt_started` command, which marks the start of a new prompt, and so the start of a new block. */
internal fun promptStartedOsc(): String = shellIntegrationOsc("prompt_started")

/** The `prompt_finished` command, which marks the end of the prompt, and so the start of the typed command. */
internal fun promptFinishedOsc(): String = shellIntegrationOsc("prompt_finished")

/** The `command_started` command, which reports the executed [command] and marks the start of its output. */
internal fun commandStartedOsc(command: String): String =
  shellIntegrationOsc("command_started;command=${command.encodeShellIntegrationValue()}")

/** The `command_finished` command, which reports [exitCode] for the command that is running now. */
internal fun commandFinishedOsc(exitCode: Int, workingDirectory: String): String =
  shellIntegrationOsc("command_finished;exit_code=$exitCode;current_directory=${workingDirectory.encodeShellIntegrationValue()}")

/** The `aliases_received` command, which reports the shell aliases as the shell prints them. */
internal fun aliasesReceivedOsc(aliasesRaw: String): String =
  shellIntegrationOsc("aliases_received;result=${aliasesRaw.encodeShellIntegrationValue()}")

/** The `completion_finished` command, which reports the result of a completion request. */
internal fun completionFinishedOsc(result: String): String =
  shellIntegrationOsc("completion_finished;result=${result.encodeShellIntegrationValue()}")
