// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.Sdk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.idea.maven.server.MavenServerManager.Companion.getInstance
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.function.Consumer

class MavenIndexingConnectorImpl(jdk: Sdk,
                                 vmOptions: String,
                                 debugPort: Int?,
                                 mavenDistribution: MavenDistribution,
                                 multimoduleDirectory: String) :
  MavenServerConnectorBase(null, jdk, vmOptions, mavenDistribution, multimoduleDirectory, debugPort) {
  init {
    throwExceptionIfProjectDisposed = false
  }

  override fun isCompatibleWith(jdk: Sdk, vmOptions: String, distribution: MavenDistribution): Boolean {
    throw UnsupportedOperationException()
  }

  override fun newStartServerTask(): StartIndexingServerTask {
    return StartIndexingServerTask()
  }

  override fun cleanUpFutures() {
  }

  override val supportType: String
    get() {
      val support = mySupport
      return if (support == null) "INDEX-?" else "INDEX-" + support.type()
    }

  inner class StartIndexingServerTask : Runnable {
    override fun run() {
      val indicator: ProgressIndicator = EmptyProgressIndicator()
      val dirForLogs = myMultimoduleDirectories.iterator().next()
      MavenLog.LOG.debug("Connecting maven connector in $dirForLogs")
      try {
        if (myDebugPort != null) {
          println("Listening for transport dt_socket at address: $myDebugPort")
        }
        val factory = MavenRemoteProcessSupportFactory.forIndexer()
        mySupport = factory.createIndexerSupport(jdk, vmOptions, mavenDistribution, myDebugPort)
        mySupport!!.onTerminate(Consumer {
          MavenLog.LOG.debug("[connector] terminate " + this@MavenIndexingConnectorImpl)
          getInstance().shutdownConnector(this@MavenIndexingConnectorImpl, false)
        })
        // the computation below spawns an immortal server that will not terminate
        // if someone is interested in the termination of the current computation, they do not need to wait for maven to terminate.
        // hence, we spawn the server in the context of maven plugin, so that it has cancellation of all other maven processes
        MavenCoroutineScopeProvider.getCoroutineScope(project).async(context = Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
          runCatching { // we need to avoid killing the coroutine by the thrown exception
            val server = mySupport!!.acquire(this, "", indicator)
            myServerPromise.setResult(server)
          }
        }.asCompletableFuture()
          .get() // there are no suspensions inside, so this code will not block
          .getOrThrow()
        MavenLog.LOG.debug("[connector] in " + dirForLogs + " has been connected " + this@MavenIndexingConnectorImpl)
      }
      catch (e: Throwable) {
        MavenLog.LOG.warn("[connector] cannot connect in $dirForLogs", e)
        myServerPromise.setError(e)
      }
    }
  }

  companion object {
    val LOG: Logger = Logger.getInstance(MavenIndexingConnectorImpl::class.java)
  }
}


