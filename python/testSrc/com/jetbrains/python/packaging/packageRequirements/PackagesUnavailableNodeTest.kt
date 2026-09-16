// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.packageRequirements

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorImpl
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.Exe
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.packaging.management.PythonPackageManager.Companion.PackageManagerErrorMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What the packages view says when the tool could not report the packages — an empty `uv.lock`, for one. An empty list
 * read as "this project has no packages", which is not what a refused `uv tree` means (PY-90174).
 */
@Subsystems.PackagingRequirements
@Layers.Functional
class PackagesUnavailableNodeTest {

  private val outOfSync = PackageManagerErrorMessage("The lock file at `uv.lock` is out of sync", "Update uv lock")

  @Test
  fun `the description is what actually failed`() {
    val state = packagesUnavailableNode(MessageError("uv: command not found"), outOfSync, canUpdateLocked = true)

    // Not "the lock file is out of sync": that message describes one condition, and a manager reports it whatever
    // went wrong, so it would tell the user something untrue about a missing executable.
    assertThat(state.description).isEqualTo("uv: command not found")
  }

  @Test
  fun `a failed process is named, not quoted`() {
    val state = packagesUnavailableNode(uvTreeFailure(), outOfSync, canUpdateLocked = true)

    // The panel is one narrow column. The command line, the stdout, the stderr and the exit code do not fit it, and
    // the Process Output tool window already holds all of them.
    assertThat(state.description).isEqualTo("The command uv tree failed")
    assertThat(state.description).doesNotContain("--frozen", "TOML parse error", "/bin/uv")
  }

  @Test
  fun `the failed process is kept, so the view can link to its output`() {
    val state = packagesUnavailableNode(uvTreeFailure(), outOfSync, canUpdateLocked = true)

    assertThat(state.failedProcess).isNotNull()
  }

  @Test
  fun `a failure with no process to show keeps none`() {
    val state = packagesUnavailableNode(MessageError("boom"), outOfSync, canUpdateLocked = true)

    assertThat(state.failedProcess).isNull()
  }

  private fun uvTreeFailure(): ExecError = ExecErrorImpl(
    exe = Exe.OnTarget("/Users/somebody/.local/bin/uv"),
    args = arrayOf("tree", "--frozen", "--all-groups"),
    errorReason = ExecErrorReason.UnexpectedProcessTermination(
      EelProcessExecutionResult(
        exitCode = 2,
        stdout = ByteArray(0),
        stderr = "error: Failed to parse `uv.lock`\nCaused by: TOML parse error at line 1, column 1".encodeToByteArray(),
      )
    ),
  )

  @Test
  fun `the fix carries the manager's name for it`() {
    val state = packagesUnavailableNode(MessageError("error: Unable to find lockfile"), outOfSync, canUpdateLocked = true)

    assertThat(state.fixCommand).isEqualTo("Update uv lock")
  }

  @Test
  fun `a manager that names no fix still gets one, because it can run one`() {
    val state = packagesUnavailableNode(MessageError("boom"), syncError = null, canUpdateLocked = true)

    assertThat(state.fixCommand).isEqualTo("Sync project")
  }

  @Test
  fun `no fix is offered when the manager has no action to run`() {
    val state = packagesUnavailableNode(MessageError("boom"), outOfSync, canUpdateLocked = false)

    assertThat(state.description).isEqualTo("boom")
    assertThat(state.fixCommand).isNull()
  }
}
