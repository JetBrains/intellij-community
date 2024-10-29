// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.File
import java.rmi.RemoteException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

open class MavenServerConnectorImpl(project: Project,
                                    jdk: Sdk,
                                    vmOptions: String,
                                    debugPort: Int?,
                                    mavenDistribution: MavenDistribution,
                                    multimoduleDirectory: String) : MavenServerConnectorBase(project, jdk, vmOptions, mavenDistribution,
                                                                                             multimoduleDirectory, debugPort) {
  private val myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("Maven connector pulling", 2)
  private val myLoggerConnectFailedCount = AtomicInteger(0)
  private val myDownloadConnectFailedCount = AtomicInteger(0)

  private var myPullingLoggerFuture: ScheduledFuture<*>? = null
  private var myPullingDownloadFuture: ScheduledFuture<*>? = null


  override fun isCompatibleWith(anotherJdk: Sdk, otherVmOptions: String, distribution: MavenDistribution): Boolean {
    if (!mavenDistribution.compatibleWith(distribution)) {
      return false
    }
    if (!StringUtil.equals(jdk.name, anotherJdk.name)) {
      return false
    }
    return StringUtil.equals(vmOptions, otherVmOptions)
  }

  override fun newStartServerTask(): StartServerTask {
    return StartServerTask()
  }

  override fun cleanUpFutures() {
    try {
      cancelFuture(myPullingDownloadFuture)
      cancelFuture(myPullingLoggerFuture)
      if (!myExecutor.isShutdown) {
        myExecutor.shutdownNow()
      }
      var count = myLoggerConnectFailedCount.get()
      if (count != 0) MavenLog.LOG.warn("Maven pulling logger failed: $count times")
      count = myDownloadConnectFailedCount.get()
      if (count != 0) MavenLog.LOG.warn("Maven pulling download listener failed: $count times")
    }
    catch (ignore: IllegalStateException) {
    }
  }

  override val supportType: String
    get() {
      val support = mySupport
      return if (support == null) "???" else support.type()
    }

  inner class StartServerTask : Runnable {
    override fun run() {
      val indicator: ProgressIndicator = EmptyProgressIndicator()
      val dirForLogs = myMultimoduleDirectories.iterator().next()
      MavenLog.LOG.debug("Connecting maven connector in $dirForLogs")
      try {
        if (myDebugPort != null) {
          println("Listening for transport dt_socket at address: $myDebugPort")
        }
        val factory = MavenRemoteProcessSupportFactory.forProject(project!!)
        mySupport = factory.create(jdk, vmOptions, mavenDistribution, project, myDebugPort)
        mySupport!!.onTerminate(Consumer {
          MavenLog.LOG.debug("[connector] terminate " + this@MavenServerConnectorImpl)
          val mavenServerManager = ApplicationManager.getApplication().getServiceIfCreated(
            MavenServerManager::class.java)
          mavenServerManager?.shutdownConnector(this@MavenServerConnectorImpl, false)
        })
        // the computation below spawns an immortal server that will not terminate
        // if someone is interested in the termination of the current computation, they do not need to wait for maven to terminate.
        // hence, we spawn the server in the context of maven plugin, so that it has cancellation of all other maven processes
        MavenCoroutineScopeProvider.getCoroutineScope(project).async(context = Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
          runCatching {
            val server = mySupport!!.acquire(this, "", indicator)
            startPullingDownloadListener(server)
            startPullingLogger(server)
            myServerPromise.setResult(server)
          }
        }.asCompletableFuture()
          .get() // there are no suspensions inside, so this code will not block
          .getOrThrow()
        MavenLog.LOG.debug("[connector] in " + dirForLogs + " has been connected " + this@MavenServerConnectorImpl)
      }
      catch (e: Throwable) {
        MavenLog.LOG.warn("[connector] cannot connect in $dirForLogs", e)
        myServerPromise.setError(e)
      }
    }
  }

  @Throws(RemoteException::class)
  private fun startPullingDownloadListener(server: MavenServer) {
    val listener = server.createPullDownloadListener(MavenRemoteObjectWrapper.ourToken)
    if (listener == null) return
    myPullingDownloadFuture = myExecutor.scheduleWithFixedDelay(
      {
        try {
          val artifactEvents = listener.pull()
          for (e in artifactEvents) {
            ApplicationManager.getApplication().messageBus.syncPublisher(MavenServerConnector.DOWNLOAD_LISTENER_TOPIC).artifactDownloaded(
              File(e.file), e.path)
          }
          myDownloadConnectFailedCount.set(0)
        }
        catch (e: RemoteException) {
          if (!Thread.currentThread().isInterrupted) {
            myDownloadConnectFailedCount.incrementAndGet()
          }
          MavenLog.LOG.warn("Maven pulling download listener stopped")
          myPullingDownloadFuture!!.cancel(true)
        }
      },
      500,
      500,
      TimeUnit.MILLISECONDS)
  }


  @Throws(RemoteException::class)
  private fun startPullingLogger(server: MavenServer) {
    val logger = server.createPullLogger(MavenRemoteObjectWrapper.ourToken)
    if (logger == null) return
    myPullingLoggerFuture = myExecutor.scheduleWithFixedDelay(
      {
        try {
          val logEvents = logger.pull()
          for (e in logEvents) {
            when (e.type) {
              ServerLogEvent.Type.DEBUG -> MavenLog.LOG.debug(e.message)
              ServerLogEvent.Type.PRINT, ServerLogEvent.Type.INFO -> MavenLog.LOG.info(e.message)
              ServerLogEvent.Type.WARN, ServerLogEvent.Type.ERROR -> MavenLog.LOG.warn(e.message)
            }
          }
          myLoggerConnectFailedCount.set(0)
        }
        catch (e: RemoteException) {
          if (!Thread.currentThread().isInterrupted) {
            myLoggerConnectFailedCount.incrementAndGet()
          }
          MavenLog.LOG.warn("Maven pulling logger stopped")
          myPullingLoggerFuture!!.cancel(true)
        }
      },
      0,
      100,
      TimeUnit.MILLISECONDS)
  }

  companion object {
    val LOG: Logger = Logger.getInstance(MavenServerConnectorImpl::class.java)

    private fun cancelFuture(future: ScheduledFuture<*>?) {
      if (future != null) {
        try {
          future.cancel(true)
        }
        catch (ignore: Throwable) {
        }
      }
    }
  }
}


