// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.python.pixi.backend.workspace.PixiWorkspaceOperations
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiWorkspaceOperationsTest {
  @Test
  fun `concurrent dependency changes preserve both manifest changes`(@TempDir root: Path): Unit = timeoutRunBlocking {
    withContext(Dispatchers.IO) {
      var manifest = "original"
      val firstStarted = CompletableDeferred<Unit>()
      val finishFirst = CompletableDeferred<Unit>()
      val first = launch {
        PixiWorkspaceOperations.run(root) {
          val updated = "$manifest first"
          firstStarted.complete(Unit)
          finishFirst.await()
          manifest = updated
        }
      }
      firstStarted.await()
      val install = async(start = CoroutineStart.UNDISPATCHED) {
        PixiWorkspaceOperations.run(root) { manifest += " second" }
      }
      assertThat(install.isCompleted).isFalse()
      finishFirst.complete(Unit)
      first.join()
      install.await()
      assertThat(manifest).isEqualTo("original first second")
    }
  }

  @Test
  fun `different workspaces can run commands concurrently`(@TempDir root: Path): Unit = timeoutRunBlocking {
    PixiWorkspaceOperations.run(root) {
      val other = this@timeoutRunBlocking.async {
        PixiWorkspaceOperations.run(root.resolve("other").createDirectories()) { "other workspace" }
      }
      assertThat(other.await()).isEqualTo("other workspace")
    }
  }

  @Test
  fun `two paths to one workspace share the operation lock`(@TempDir root: Path): Unit = timeoutRunBlocking {
    withContext(Dispatchers.IO) {
      val alias = root.resolve("nested").createDirectories().resolve("..")
      val waiting = PixiWorkspaceOperations.run(root) {
        this@withContext.async(start = CoroutineStart.UNDISPATCHED) { PixiWorkspaceOperations.run(alias) { "same workspace" } }.also {
          assertThat(it.isCompleted).isFalse()
        }
      }
      assertThat(waiting.await()).isEqualTo("same workspace")
    }
  }

  @Test
  fun `a cancelled waiter does not release another operation`(@TempDir root: Path): Unit = timeoutRunBlocking {
    withContext(Dispatchers.IO) {
      PixiWorkspaceOperations.run(root) {
        val cancelled = this@withContext.launch(start = CoroutineStart.UNDISPATCHED) {
          PixiWorkspaceOperations.run(root) { error("Cancelled operation ran.") }
        }
        cancelled.cancelAndJoin()
        val waiting = this@withContext.launch(start = CoroutineStart.UNDISPATCHED) { PixiWorkspaceOperations.run(root) { } }
        assertThat(waiting.isCompleted).isFalse()
        waiting.cancelAndJoin()
      }
      assertThat(PixiWorkspaceOperations.run(root) { "released" }).isEqualTo("released")
    }
  }

  @Test
  fun `a cancelled owner releases its workspace`(@TempDir root: Path): Unit = timeoutRunBlocking {
    val entered = CompletableDeferred<Unit>()
    val owner = launch {
      PixiWorkspaceOperations.run(root) {
        entered.complete(Unit)
        CompletableDeferred<Unit>().await()
      }
    }
    entered.await()
    owner.cancelAndJoin()
    assertThat(PixiWorkspaceOperations.run(root) { "released" }).isEqualTo("released")
  }
}
