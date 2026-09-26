// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.icons.AllIcons
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.EvoNodeStats
import com.intellij.python.sdk.frontend.evolution.components.EvoLoadedNode
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeSection
import com.intellij.python.sdk.frontend.evolution.components.State
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards that loading a lazy node always ends somewhere.
 *
 * The node holds a "Loading…" row until its loader answers, and the widget keeps its tree across opens. A load that
 * ends without setting a state therefore does not fail once: the row spins for the life of the tree, and every later
 * open of that node shows the same spinner. The load used to name the three failures it expected and nothing else,
 * which left anything else — a bug in a loader, a cancelled progress — stranding the node that way.
 */
@TestApplication
class PyEvoLazyNodeLoadTest {
  private val projectFixture = projectFixture()

  /** Loads a node and waits for the load to end, however it ends. Returns the state it ended in. */
  private fun stateAfterLoading(loader: suspend (Boolean) -> EvoLoadedNode): State {
    val node = EvoTreeLazyNodeElement(
      text = "node",
      icon = AllIcons.Language.Python,
      nodeStats = EvoNodeStats(EvoNodeKind.OTHER),
      loader = loader,
    )
    val finished = CompletableDeferred<Unit>()
    // Registered before the load starts, and it fires on the first state that is not LOADING — which is the whole
    // question here. A stranded node never fires it, and the timeout below is what says so.
    node.whenLoadFinished { finished.complete(Unit) }
    // A loader that throws leaves its own coroutine failing, exactly as it does in the widget. Under a supervisor that
    // stays this test's business rather than the runner's, and what is under test is the state the node is left in.
    return runBlocking {
      // Supervised, so the failing load is this test's business rather than the runner's, and with a handler because
      // a loader that throws leaves its own coroutine failing — exactly as it does in the widget. What is under test
      // is the state the node is left in.
      val scope = childScope("PyEvoLazyNodeLoadTest", CoroutineExceptionHandler { _, _ -> })
      node.load(projectFixture.get(), scope, emptyList())
      withTimeout(60.seconds) { finished.await() }
      scope.cancel()
      node.state
    }
  }

  @Test
  fun `a loader that answers leaves the node done`() {
    assertEquals(State.DONE, stateAfterLoading { EvoLoadedNode(sections = listOf(EvoTreeSection(elements = emptyList())), refreshable = false) })
  }

  @Test
  fun `a loader that throws anything at all does not leave the node loading`() {
    // None of the widget's own failure kinds — a bug inside a loader looks exactly like this. Put back as never
    // loaded, which is what lets the next open try again instead of showing a row that spins for good.
    assertEquals(State.CREATED, stateAfterLoading { throw IllegalStateException("boom") })
  }

  @Test
  fun `a cancelled load is put back, so opening the node again asks again`() {
    // CREATED is what makes the next open load it: a step loads the elements it finds in that state.
    assertEquals(State.CREATED, stateAfterLoading { throw CancellationException("progress cancelled") })
  }
}
