/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server

import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.rmi.RemoteException

abstract class RemoteObjectWrapper<T> protected constructor() {
  protected var myWrappee: T? = null

  protected val wrappee: T
    get() = myWrappee!!

  @Throws(RemoteException::class)
  protected open suspend fun getOrCreateWrappee(): T {
    if (myWrappee == null) {
      myWrappee = create()
    }
    return myWrappee!!
  }

  @Throws(RemoteException::class)
  protected abstract suspend fun create(): T

  @Synchronized
  protected open fun handleRemoteError(e: RemoteException) {
    MavenLog.LOG.debug("[connector] Connection failed. Will be reconnected on the next request.", e)
    onError()
  }

  @Synchronized
  protected fun onError() {
    cleanup()
  }

  @Synchronized
  protected open fun cleanup() {
    myWrappee = null
  }

  protected fun <R, E : Exception?> perform(r: Retriable<R, E>): R {
    var last: RemoteException? = null
    for (i in 0..1) {
      try {
        return r.execute()
      }
      catch (e: RemoteException) {
        handleRemoteError(e.also { last = it })
      }
    }
    throw CannotStartServerException(SyncBundle.message("maven.cannot.reconnect"), last)
  }

  @Throws(MavenProcessCanceledException::class)
  protected fun <R, E : Exception?> performCancelable(indicator: MavenProgressIndicator, r: RetriableCancelable<R, E>): R {
    var last: RemoteException? = null
    for (i in 0..1) {
      try {
        if (!indicator.isCanceled) {
          return r.execute()
        }
      }
      catch (e: RemoteException) {
        handleRemoteError(e.also { last = it })
      }
      catch (e: MavenServerProcessCanceledException) {
        throw MavenProcessCanceledException()
      }
    }
    throw CannotStartServerException(SyncBundle.message("maven.cannot.reconnect"), last)
  }

  fun interface Retriable<T, E : Exception?> {
    @Throws(RemoteException::class)
    fun execute(): T
  }

  protected fun interface RetriableCancelable<T, E : Exception?> {
    @Throws(RemoteException::class, MavenServerProcessCanceledException::class)
    fun execute(): T
  }
}
