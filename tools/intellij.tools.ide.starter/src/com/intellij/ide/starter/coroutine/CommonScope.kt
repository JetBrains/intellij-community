package com.intellij.ide.starter.coroutine

import kotlinx.coroutines.*

/**
 * Lifespan is as long as the entire test suite run. When the test suite is finished, a whole coroutines tree will be canceled.
 * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
 */
val testSuiteSupervisorScope: CoroutineScope =
  CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Test suite's supervisor scope"))

/**
 * Lifespan is limited to the duration of the test class. By the end of the test class whole coroutines tree will be cancelled.
 * This scope is canceled automatically if testSuiteSupervisorScope is canceled (not the other way around).
 */
val perClassSupervisorScope: CoroutineScope =
  CoroutineScope(SupervisorJob(testSuiteSupervisorScope.coroutineContext[Job]) + Dispatchers.IO + CoroutineName("Test class's supervisor scope"))

/**
 * Lifespan is limited to duration each test. By the end of the test a whole coroutines tree will be canceled.
 * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
 * Usually that is what you need.
 * This scope is canceled automatically if perClassSupervisorScope is canceled (not the other way around).
 */
val perTestSupervisorScope: CoroutineScope =
  CoroutineScope(SupervisorJob(perClassSupervisorScope.coroutineContext[Job]) + Dispatchers.IO + CoroutineName("Test's supervisor scope"))


/**
 * In case of unhandled exception in child coroutines, all the coroutines tree (parents and other branches) will be canceled.
 * In most scenarios you don't need that behavior.
 */
val simpleScope: CoroutineScope = CoroutineScope(Job() + Dispatchers.IO + CoroutineName("Simple scope"))