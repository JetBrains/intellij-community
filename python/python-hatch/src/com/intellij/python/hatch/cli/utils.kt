package com.intellij.python.hatch.cli

import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.stderrString

/**
 * Hatch has two versions of messages in the stop function: "Expected prefix ..." (old_message) and "Finished expected prefix ..." (final_text)
 * https://github.com/pypa/hatch/blob/4ebce0e1fe8bf0fcdef587a704c207a063d72575/src/hatch/cli/terminal.py#L65
 */
internal fun EelProcessExecutionResult.isSuccessStop(expectedPrefix: String): Boolean {
  require(expectedPrefix.isNotEmpty()) { "Expected prefix can't be empty" }
  if (exitCode != 0) return false

  val response = stderrString
  val result = response.startsWith(expectedPrefix) ||
               response.startsWith("Finished ${expectedPrefix.first().lowercase()}${expectedPrefix.drop(1)}")

  return result
}