// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.testFramework.junit

/**
 * Remembers only the IDE-side JUnit lifecycle callbacks that reached the current lambda session.
 *
 * A recycled IDE has a new RD session. The JUnit engine does not repeat callbacks for a test that is already
 * running, so the replacement session needs the successfully delivered `beforeAll` callbacks followed by the
 * active `beforeEach`. Host-side extensions are deliberately outside this coordinator and are never replayed.
 */
internal class BackgroundLambdaLifecycleCoordinator {
  private val lock = Any()
  private val activeBeforeAll = LinkedHashMap<String, LifecycleCallback>()
  private var activeBeforeEach: ActiveTest? = null
  private var synchronizedIdeIdentity: Any? = null

  fun beforeAllDelivered(className: String, callbackName: String, ideIdentity: Any) {
    synchronized(lock) {
      val callback = LifecycleCallback(LifecyclePhase.BEFORE_ALL, callbackName)
      val previous = activeBeforeAll.put(className, callback)
      check(previous == null || previous == callback) {
        "beforeAll for '$className' was delivered twice with different contexts: '$previous' and '$callback'"
      }
      synchronizedIdeIdentity = ideIdentity
    }
  }

  fun beforeEachDelivered(testId: String, className: String, callbackName: String, ideIdentity: Any) {
    synchronized(lock) {
      check(activeBeforeEach == null) {
        "beforeEach for '$testId' was delivered while '${activeBeforeEach?.testId}' is still active"
      }
      activeBeforeEach = ActiveTest(
        testId = testId,
        className = className,
        callback = LifecycleCallback(LifecyclePhase.BEFORE_EACH, callbackName),
      )
      synchronizedIdeIdentity = ideIdentity
    }
  }

  fun afterEachFinished(testId: String) {
    synchronized(lock) {
      val active = activeBeforeEach ?: return
      check(active.testId == testId) {
        "afterEach for '$testId' does not match active test '${active.testId}'"
      }
      activeBeforeEach = null
    }
  }

  fun afterAllFinished(className: String) {
    synchronized(lock) {
      activeBeforeAll.remove(className)
      if (activeBeforeEach?.className == className) {
        activeBeforeEach = null
      }
      if (activeBeforeAll.isEmpty() && activeBeforeEach == null) {
        synchronizedIdeIdentity = null
      }
    }
  }

  /** Returns `true` only when callbacks were replayed to a previously unseen replacement IDE. */
  suspend fun replayAfterRecycle(
    ideIdentity: Any,
    deliver: suspend (LifecycleCallback) -> Unit,
  ): Boolean {
    val plan = synchronized(lock) {
      val activeTest = activeBeforeEach ?: return false
      if (synchronizedIdeIdentity === ideIdentity) return false
      ReplayPlan(
        beforeAll = activeBeforeAll.values.toList(),
        beforeEach = activeTest,
      )
    }

    for (callback in plan.beforeAll) {
      deliver(callback)
    }
    deliver(plan.beforeEach.callback)

    synchronized(lock) {
      check(activeBeforeAll.values.toList() == plan.beforeAll && activeBeforeEach == plan.beforeEach) {
        "IDE-side JUnit lifecycle changed while it was being replayed"
      }
      synchronizedIdeIdentity = ideIdentity
    }
    return true
  }

  private data class ActiveTest(
    val testId: String,
    val className: String,
    val callback: LifecycleCallback,
  )

  private data class ReplayPlan(
    val beforeAll: List<LifecycleCallback>,
    val beforeEach: ActiveTest,
  )
}

internal data class LifecycleCallback(
  val phase: LifecyclePhase,
  val callbackName: String,
)

internal enum class LifecyclePhase {
  BEFORE_ALL,
  BEFORE_EACH,
}
