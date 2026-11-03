// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.Disposable
import com.intellij.python.sdk.backend.PythonEnvironmentContext
import com.intellij.python.sdk.backend.PythonEnvironmentFacts
import com.intellij.python.sdk.backend.PythonEnvironmentKey
import com.intellij.python.sdk.backend.PythonEnvironmentStep
import com.intellij.python.sdk.backend.PythonEnvironments
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/** The updates of the Python environments, see [PythonEnvironments]: one update for each environment on the disk. */
@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PythonEnvironmentsTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  /** The environments that [CountingStep] updated, in their order. */
  private val updated = CopyOnWriteArrayList<PythonEnvironmentKey>()

  /** Completes when an update starts, and lets it end, see [CountingStep]. */
  private var started = CompletableDeferred<Unit>()
  private var release = CompletableDeferred<Unit>().apply { complete(Unit) }

  /** An awaited step, so it runs in [PythonEnvironments.update]. */
  private inner class CountingStep : PythonEnvironmentStep {
    override val awaited: Boolean get() = true

    override suspend fun update(context: PythonEnvironmentContext, facts: PythonEnvironmentFacts): PythonEnvironmentFacts {
      updated += context.key
      started.complete(Unit)
      release.await()
      return facts
    }
  }

  /** The background steps that ran, in their order, with the version that the awaited step gave them. */
  private val backgroundRuns = CopyOnWriteArrayList<String?>()

  /** A step that is not awaited, so it runs in [PythonEnvironments.updateInBackground], after the awaited steps. */
  private inner class BackgroundStep : PythonEnvironmentStep {
    override suspend fun update(context: PythonEnvironmentContext, facts: PythonEnvironmentFacts): PythonEnvironmentFacts {
      backgroundRuns += facts.version
      return facts.copy(sysPath = listOf("background"))
    }
  }

  @BeforeEach
  fun registerStep() {
    // Only these steps run: the real probe and skeletons would run for the mock SDK too.
    (PythonEnvironmentStep.EP_NAME.point as com.intellij.openapi.extensions.impl.ExtensionPointImpl<PythonEnvironmentStep>)
      .maskAll(listOf(CountingStep(), BackgroundStep()), disposable, false)
  }

  @Test
  fun `a background update runs after an awaited one and shares its facts`(): Unit = timeoutRunBlocking {
    val first = PythonMockSdk.create()
    val second = PythonMockSdk.create()

    val facts = PythonEnvironments.getInstance().updateInBackground(first).getOrThrow()
    PythonEnvironments.getInstance().updateInBackground(second).getOrThrow()

    assertThat(updated).describedAs("Each background update runs the awaited steps before it").hasSize(2)
    assertThat(backgroundRuns).hasSize(2)
    assertThat(facts.sysPath).describedAs("A background step adds its facts").containsExactly("background")
  }

  @Test
  fun `two SDKs of one environment share one update`(): Unit = timeoutRunBlocking {
    val first = PythonMockSdk.create()
    val second = PythonMockSdk.create()
    started = CompletableDeferred()
    release = CompletableDeferred()

    val running = async { PythonEnvironments.getInstance().update(first) }
    started.await()
    // Both requests come during the running update: they share one more.
    val a = async { PythonEnvironments.getInstance().update(first) }
    val b = async { PythonEnvironments.getInstance().update(second) }
    yield()
    release.complete(Unit)
    running.await()
    a.await()
    b.await()

    assertThat(updated).hasSize(2)
    assertThat(updated.toSet()).hasSize(1)
  }

  @Test
  fun `requests one after another update each time`(): Unit = timeoutRunBlocking {
    val sdk = PythonMockSdk.create()

    PythonEnvironments.getInstance().update(sdk)
    PythonEnvironments.getInstance().update(sdk)

    assertThat(updated).hasSize(2)
  }
}
