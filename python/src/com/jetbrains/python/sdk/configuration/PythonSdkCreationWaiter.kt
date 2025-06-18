// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
object PythonSdkCreationWaiter {
  private val KEY = Key<CompletableFuture<Unit>>("PythonSdkCreationWaiter")

  suspend fun waitCreatingSdk(module: Module) {
    val future = synchronized(module) {
      module.getUserData(KEY)
    }
    future?.await()
  }

  fun isCreatingSdk(module: Module): Boolean {
    return synchronized(module) {
      module.getUserData(KEY) != null
    }
  }

  fun register(module: Module, lifetime: Disposable) {
    val oldData = synchronized(module) {
      val old = module.getUserData(KEY)
      module.putUserData(KEY, CompletableFuture())
      Disposer.register(lifetime) {
        val future = synchronized(module) {
          val future = module.getUserData(KEY)
          module.putUserData(KEY, null)
          future
        }
        future?.complete(Unit)
      }
      old
    }
    oldData?.complete(Unit)
  }
}