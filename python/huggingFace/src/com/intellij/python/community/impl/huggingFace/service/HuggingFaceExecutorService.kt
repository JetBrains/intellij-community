// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlin.time.Duration


@Service(Service.Level.APP)
class HuggingFaceExecutorService(val coroutineScope: CoroutineScope)

@Service(Service.Level.APP)
class HuggingFaceSafeExecutor(private val coroutineScope: CoroutineScope, private val defaultTimeout: Duration): Disposable {

  constructor(coroutineScope: CoroutineScope) : this(coroutineScope, Duration.INFINITE)

  override fun dispose() {
    coroutineScope.cancel("$SERVICE_NAME dispose")
  }

  fun <T> asyncSuspend(taskName: String, body: suspend CoroutineScope.() -> T): Deferred<T> {
    return coroutineScope.async(context = CoroutineName(taskName)) {
      withTimeout(defaultTimeout) { body() }
    }
  }

  companion object {
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getInstance(
      name: String = SERVICE_NAME,
      defaultTimeout: Duration = Duration.INFINITE,
      parallelism: Int = 10
    ): HuggingFaceSafeExecutor {

      val childScope = service<HuggingFaceExecutorService>().coroutineScope.childScope(
        Dispatchers.IO.limitedParallelism(parallelism) + CoroutineName(name))

      return HuggingFaceSafeExecutor(childScope, defaultTimeout)
    }

    val instance by lazy { getInstance() }

    const val SERVICE_NAME = "HuggingFaceSafeExecutor"
    val LOG = Logger.getInstance(HuggingFaceSafeExecutor::class.java)
  }
}
