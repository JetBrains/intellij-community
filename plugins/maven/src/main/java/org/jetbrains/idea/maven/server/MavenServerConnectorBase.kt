// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.server.MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport
import org.jetbrains.idea.maven.server.MavenServerManager.Companion.getInstance
import org.jetbrains.idea.maven.utils.MavenLog
import java.rmi.RemoteException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

abstract class MavenServerConnectorBase(project: Project?,
                                        jdk: Sdk,
                                        vmOptions: String,
                                        mavenDistribution: MavenDistribution,
                                        multimoduleDirectory: String,
                                        @JvmField protected val myDebugPort: Int?) : AbstractMavenServerConnector(project, jdk,
                                                                                                                  vmOptions,
                                                                                                                  mavenDistribution,
                                                                                                                  multimoduleDirectory) {
  @JvmField
  protected var mySupport: MavenRemoteProcessSupport? = null

  private val myConnectStarted: AtomicBoolean = AtomicBoolean(false)
  private val myTerminated: AtomicBoolean = AtomicBoolean(false)

  @JvmField
  protected var throwExceptionIfProjectDisposed: Boolean = true

  @JvmField
  protected val myServerPromise: AsyncPromise<MavenServer?> = object : AsyncPromise<MavenServer?>() {
    override fun shouldLogErrors(): Boolean {
      return false
    }
  }

  override fun isNew(): Boolean {
    return !myConnectStarted.get()
  }

  protected abstract fun newStartServerTask(): Runnable

  override fun connect() {
    if (!myConnectStarted.compareAndSet(false, true)) {
      return
    }
    MavenLog.LOG.debug("connecting new maven server: $this")
    ApplicationManager.getApplication().executeOnPooledThread(newStartServerTask())
  }

  private fun waitForServerBlocking(): MavenServer? {
    while (!myServerPromise.isDone) {
      try {
        myServerPromise.get(100, TimeUnit.MILLISECONDS)
      }
      catch (ignore: Exception) {
      }
      if (throwExceptionIfProjectDisposed && project!!.isDisposed) {
        throw CannotStartServerException("Project already disposed")
      }
      ProgressManager.checkCanceled()
    }
    return myServerPromise.get()
  }

  private suspend fun waitForServer(): MavenServer? {
    while (!myServerPromise.isDone) {
      delay(100)
      if (throwExceptionIfProjectDisposed && project!!.isDisposed) {
        throw CannotStartServerException("Project already disposed")
      }
      checkCanceled()
    }
    return myServerPromise.get()
  }

  @Deprecated("use suspend", ReplaceWith("getServer"))
  override fun getServerBlocking(): MavenServer {
    try {
      val server = waitForServerBlocking()
      if (server == null) {
        throw ProcessCanceledException()
      }
      return server
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      try {
        getInstance().shutdownConnector(this, false)
      }
      catch (ignored: Throwable) {
      }
      throw if (e is CannotStartServerException) e else CannotStartServerException(e)
    }
  }

  override suspend fun getServer(): MavenServer {
    try {
      val server = waitForServer()
      if (server == null) {
        throw ProcessCanceledException()
      }
      return server
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      try {
        getInstance().shutdownConnector(this, false)
      }
      catch (ignored: Throwable) {
      }
      checkCanceled()
      throw if (e is CannotStartServerException) e else CannotStartServerException(e)
    }
  }

  @ApiStatus.Internal
  override fun stop(wait: Boolean) {
    MavenLog.LOG.debug("[connector] shutdown " + this + " " + (mySupport == null))
    cleanUpFutures()
    val support = mySupport
    support?.stopAll(wait)
    myTerminated.set(true)
  }

  override fun <R> perform(r: () -> R): R {
    var last: RemoteException? = null
    for (i in 0..1) {
      try {
        return r()
      }
      catch (e: RemoteException) {
        last = e
      }
    }
    cleanUpFutures()
    getInstance().shutdownConnector(this, false)
    MavenLog.LOG.debug("[connector] perform error $this")
    throw RuntimeException("Cannot reconnect.", last)
  }

  protected abstract fun cleanUpFutures()

  override val state: MavenServerConnector.State
    get() = when (myServerPromise.state) {
      Promise.State.SUCCEEDED -> if (myTerminated.get()) MavenServerConnector.State.STOPPED else MavenServerConnector.State.RUNNING
      Promise.State.REJECTED -> MavenServerConnector.State.FAILED
      else -> MavenServerConnector.State.STARTING
    }

  override fun checkConnected(): Boolean {
    val support = mySupport
    return support != null && !support.activeConfigurations.isEmpty()
  }

  override suspend fun ping(): Boolean {
    try {
      val pinged = getServer().ping(MavenRemoteObjectWrapper.ourToken)
      if (MavenLog.LOG.isTraceEnabled) {
        MavenLog.LOG.trace("maven server ping: $pinged")
      }
      return pinged
    }
    catch (e: RemoteException) {
      MavenLog.LOG.warn("maven server ping error", e)
      return false
    }
  }
}
