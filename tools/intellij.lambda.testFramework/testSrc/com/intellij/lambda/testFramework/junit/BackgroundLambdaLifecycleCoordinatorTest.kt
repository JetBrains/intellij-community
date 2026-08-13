// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.testFramework.junit

import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
internal class BackgroundLambdaLifecycleCoordinatorTest {
  @Test
  fun `replays before all then active before each once per replacement IDE`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val coordinator = BackgroundLambdaLifecycleCoordinator()
    val originalIde = Any()
    val firstReplacement = Any()
    val secondReplacement = Any()
    val delivered = ArrayList<String>()
    coordinator.beforeAllDelivered("example.Suite", "Before all", originalIde)
    coordinator.beforeEachDelivered("test-id", "example.Suite", "Before each", originalIde)

    assertThat(coordinator.replayAfterRecycle(firstReplacement) { callback -> delivered += callback.callbackName }).isTrue()
    assertThat(coordinator.replayAfterRecycle(firstReplacement) { callback -> delivered += callback.callbackName }).isFalse()
    assertThat(coordinator.replayAfterRecycle(secondReplacement) { callback -> delivered += callback.callbackName }).isTrue()

    assertThat(delivered).containsExactly("Before all", "Before each", "Before all", "Before each")
  }

  @Test
  fun `replays before all between test methods`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val coordinator = BackgroundLambdaLifecycleCoordinator()
    val delivered = ArrayList<LifecycleCallback>()
    coordinator.beforeAllDelivered("example.Suite", "Before all", Any())

    assertThat(coordinator.replayAfterRecycle(Any(), delivered::add)).isTrue()
    assertThat(delivered).containsExactly(LifecycleCallback(LifecyclePhase.BEFORE_ALL, "Before all"))
  }

  @Test
  fun `does not replay with no active lifecycle`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val coordinator = BackgroundLambdaLifecycleCoordinator()
    val delivered = ArrayList<LifecycleCallback>()

    assertThat(coordinator.replayAfterRecycle(Any(), delivered::add)).isFalse()
    assertThat(delivered).isEmpty()
  }

  @Test
  fun `replays only callbacks that reached the original IDE`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val coordinator = BackgroundLambdaLifecycleCoordinator()
    val delivered = ArrayList<LifecycleCallback>()
    coordinator.beforeEachDelivered("test-id", "example.Suite", "Before each", Any())

    assertThat(coordinator.replayAfterRecycle(Any(), delivered::add)).isTrue()

    assertThat(delivered).containsExactly(LifecycleCallback(LifecyclePhase.BEFORE_EACH, "Before each"))
  }

  @Test
  fun `after each removes before each but preserves before all replay`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val coordinator = BackgroundLambdaLifecycleCoordinator()
    val delivered = ArrayList<LifecycleCallback>()
    coordinator.beforeAllDelivered("example.Suite", "Before all", Any())
    coordinator.beforeEachDelivered("test-id", "example.Suite", "Before each", Any())

    coordinator.afterEachFinished("test-id")

    assertThat(coordinator.replayAfterRecycle(Any(), delivered::add)).isTrue()
    assertThat(delivered).containsExactly(LifecycleCallback(LifecyclePhase.BEFORE_ALL, "Before all"))
  }

  @Test
  fun `failed replay is not marked complete`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val coordinator = BackgroundLambdaLifecycleCoordinator()
    val replacement = Any()
    val delivered = ArrayList<String>()
    coordinator.beforeAllDelivered("example.Suite", "Before all", Any())
    coordinator.beforeEachDelivered("test-id", "example.Suite", "Before each", Any())

    val failure = runCatching {
      coordinator.replayAfterRecycle(replacement) { callback ->
        delivered += callback.callbackName
        if (callback.phase == LifecyclePhase.BEFORE_EACH) error("replay failed")
      }
    }.exceptionOrNull()

    assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("replay failed")

    assertThat(coordinator.replayAfterRecycle(replacement) { callback -> delivered += callback.callbackName }).isTrue()
    assertThat(delivered).containsExactly("Before all", "Before each", "Before all", "Before each")
  }
}
