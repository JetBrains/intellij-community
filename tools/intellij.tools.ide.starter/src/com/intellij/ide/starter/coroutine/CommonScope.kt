package com.intellij.ide.starter.coroutine

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Lifespan is as long as entire test suite run. When test suite is finished whole coroutines tree will be cancelled.
 * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
 * Usually that what you need.
 */
val testSuiteSupervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Test suite's supervisor scope"))

/**
 * Lifespan is limited to duration each test. By the end of the test whole coroutines tree will be cancelled.
 * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
 * Usually that what you need.
 */
val perTestSupervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Test's supervisor scope"))

/**
 * Lifespan is limited to duration of the test class. By the end of the test class whole coroutines tree will be cancelled.
 */
val perClassSupervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Test class's supervisor scope"))

/**
 * In case of unhandled exception in child coroutines all the coroutines tree (parents and other branches) will be cancelled.
 * In most scenarious you don't need that behaviour.
 */
val simpleScope = CoroutineScope(Job() + Dispatchers.IO + CoroutineName("Simple scope"))