// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.frameActivationCache

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/**
 * First, create an instance of this class statically (it is not big) providing unique name and type you want to cache
 * ```kotlin
 * val keys = CacheKeys<MyDataClass>("myIde-mySubsystem")
 * ```
 * then, see [UserDataHolderEx.getOrComputeOnFrameActivation]
 */
class CacheKeys<T : Any> private constructor(internal val cacheKey: Key<CacheHolderImpl<T>>, internal val mutexKey: Key<Mutex>) {
  constructor(label: @NlsSafe String) : this(Key.create<CacheHolderImpl<T>>("$label-cache"), Key.create<Mutex>("$label-mutex"))
}


/**
 * Be sure you have static (e.g. top level) [CacheKeys] `val`, and now call
 * ```kotlin
 * private val keys = CacheKeys<MyDataClass>("myIde-mySubsystem")
 *
 * fun doAll() {
 *     // data is cached in dataHolder, but flushed when user activates an IDE frame
 *     val data = dataHolder.getOrComputeOnFrameActivation(keys) { calculateData() }
 * }
 * ```
 * Be sure [UserDataHolderEx] doesn't outlive data returned by [getData] e.g: never store document in application: document will leak.
 */
suspend fun <T : Any> UserDataHolderEx.getOrComputeOnFrameActivation(keys: CacheKeys<T>, getData: suspend () -> T): T =
  getOrCreateUserData(keys.mutexKey) {
    Mutex()
  }.withLock {
    val cacheHolder = getUserData(keys.cacheKey)
    if (cacheHolder != null && cacheHolder.counter == FrameActivationCounter.counter) {
      cacheHolder.value
    }
    else {
      val counterAtStart = FrameActivationCounter.counter
      getData().also {
        putUserData(keys.cacheKey, CacheHolderImpl(counterAtStart, it))
      }
    }
  }

