package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class TestContextInitializedEvent(
  val container: TestContainer<*>,
  val testContext: IDETestContext,
) : Event()

/**
 * Subscribe for [TestContextInitializedEvent] that belongs to the given [container].
 * Invoked only for rem dev backend in case of rem dev.
 */
fun <T> EventsBus.subscribeForTestContextInitializedEvent(
  subscriber: Any,
  container: TestContainer<T>,
  timeout: Duration = 2.minutes,
  ignoreExceptions: Boolean = true,
  callback: suspend (event: TestContextInitializedEvent) -> Unit,
): EventsBus = subscribe<TestContextInitializedEvent>(
  subscriber = Pair(subscriber, container),
  timeout = timeout,
  ignoreExceptions = ignoreExceptions,
  callback = { event ->
    if (event.container === container
        // not rem dev or rem dev backend
        && (container !is RemDevTestContainer || event.testContext.isRemDevContext())
    ) {
      logOutput("TestContextInitializedEvent for container: ${container.javaClass.simpleName}, subscriber: $subscriber")
      callback.invoke(event)
    }
  },
)