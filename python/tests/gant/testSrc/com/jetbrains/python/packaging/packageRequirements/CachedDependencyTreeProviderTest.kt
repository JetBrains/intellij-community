// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.packageRequirements

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrNull
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * How often the provider runs its command. A startup asks four times — the tool window, the inspection, and the two
 * package-management events — and every ask ran `uv tree` again while the command was failing (PY-90174).
 */
@Subsystems.PackagingRequirements
@Layers.Functional
class CachedDependencyTreeProviderTest {

  private val runs = AtomicInteger()

  @Test
  fun `a success is fetched once`() = runTest {
    val provider = provider(state = { "state" }) { PyResult.success("flask v1.0") }

    repeat(4) { assertThat(provider.getDependencyTrees().getOrThrow()).hasSize(1) }

    assertThat(runs.get()).isEqualTo(1)
  }

  @Test
  fun `a failure is fetched once while its inputs hold`() = runTest {
    val provider = provider(state = { "the same uv lock" }) { PyResult.localizedError("error: Failed to parse `uv.lock`") }

    repeat(4) { assertThat(provider.getDependencyTrees().getOrNull()).isNull() }

    // The four callers of a startup share one failed command, instead of running it once each.
    assertThat(runs.get()).isEqualTo(1)
  }

  @Test
  fun `a failure is retried once its inputs change`() = runTest {
    var lock = "broken"
    val provider = provider(state = { lock }) { PyResult.localizedError("error: Failed to parse `uv.lock`") }

    provider.getDependencyTrees()
    lock = "the user fixed it"
    provider.getDependencyTrees()

    // Nothing invalidates this cache when the user edits the lock file by hand, so a cached failure that never
    // expired would outlive the problem — which is the bug this cache had before.
    assertThat(runs.get()).isEqualTo(2)
  }

  @Test
  fun `a failure is not kept when the inputs are unknown`() = runTest {
    val provider = provider(state = { null }) { PyResult.localizedError("boom") }

    repeat(2) { provider.getDependencyTrees() }

    assertThat(runs.get()).isEqualTo(2)
  }

  @Test
  fun `a success is kept even when the inputs are unknown`() = runTest {
    val provider = provider(state = { null }) { PyResult.success("flask v1.0") }

    repeat(2) { provider.getDependencyTrees() }

    assertThat(runs.get()).isEqualTo(1)
  }

  @Test
  fun `callers that ask while the command runs share it`() = runTest {
    val finish = CompletableDeferred<Unit>()
    val provider = provider(state = { "state" }) {
      finish.await()
      PyResult.success("flask v1.0")
    }

    coroutineScope {
      val asks = List(4) { async { provider.getDependencyTrees() } }
      finish.complete(Unit)
      asks.forEach { assertThat(it.await().getOrThrow()).hasSize(1) }
    }

    assertThat(runs.get()).isEqualTo(1)
  }

  @Test
  fun `an invalidated cache fetches again`() = runTest {
    val provider = provider(state = { "state" }) { PyResult.success("flask v1.0") }

    provider.getDependencyTrees()
    provider.invalidateCache()
    provider.getDependencyTrees()

    assertThat(runs.get()).isEqualTo(2)
  }

  private fun provider(state: suspend () -> Any?, fetch: suspend () -> PyResult<String>) =
    CachedDependencyTreeProvider(
      fetchOutput = { runs.incrementAndGet(); fetch() },
      dependenciesState = state,
    )
}
