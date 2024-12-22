// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.execution.rmi.RemoteProcessSupport
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.ThreadUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ReflectionUtil
import com.intellij.util.WaitFor
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

class MavenServerManagerTest : MavenTestCase() {
  fun testInitializingDoesntTakeReadAction() = runBlocking {
    //make sure all components are initialized to prevent deadlocks
    ensureConnected(MavenServerManager.getInstance().getConnector(project, projectRoot.path))

    val result = ApplicationManager.getApplication().runWriteAction(
      ThrowableComputable<Future<*>, Exception?> {
        ApplicationManager.getApplication().executeOnPooledThread {
          MavenServerManager.getInstance().closeAllConnectorsAndWait()
          runBlockingMaybeCancellable {
            ensureConnected(MavenServerManager.getInstance().getConnector(project, projectRoot.path))
          }
        }
      })

    val start = System.currentTimeMillis()
    val end = TimeUnit.SECONDS.toMillis(10) + start
    var ok = false
    while (System.currentTimeMillis() < end && !ok) {
      runInEdtAndWait {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }
      try {
        result.get(0, TimeUnit.MILLISECONDS)
        ok = true
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }
      catch (e: ExecutionException) {
        throw RuntimeException(e)
      }
      catch (ignore: TimeoutException) {
      }
    }
    if (!ok) {
      ThreadUtil.printThreadDump()
      fail()
    }
    result.cancel(true)
    Unit
  }

  fun testConnectorRestartAfterVMChanged() = runBlocking {
    val settingsComponent = MavenWorkspaceSettingsComponent.getInstance(project)
    val vmOptions = settingsComponent.settings.importingSettings.vmOptionsForImporter
    try {
      val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
      ensureConnected(connector)
      settingsComponent.settings.importingSettings.vmOptionsForImporter = "$vmOptions -DtestVm=test"
      assertNotSame(connector, ensureConnected(MavenServerManager.getInstance().getConnector(project, projectRoot.path)))
    }
    finally {
      settingsComponent.settings.importingSettings.vmOptionsForImporter = vmOptions
    }
  }

  fun testShouldRestartConnectorAutomaticallyIfFailed() = runBlocking {
    val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
    ensureConnected(connector)
    kill(connector)
    val newConnector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
    ensureConnected(newConnector)
    assertNotSame(connector, newConnector)
  }


  fun testShouldStopPullingIfConnectorIsFailing() = runBlocking {
    val connector = MavenServerManager.getInstance().getConnector(project, projectRoot.path)
    ensureConnected(connector)
    val executor = ReflectionUtil.getField(
      MavenServerConnectorImpl::class.java, connector, ScheduledExecutorService::class.java, "myExecutor")
    kill(connector)
    object : WaitFor(1000) {
      override fun condition(): Boolean {
        return executor.isShutdown
      }
    }
    assertTrue(executor.isShutdown)
  }

  fun testShouldDropConnectorForMultiplyDirs() = runBlocking {
    val topDir = projectRoot.toNioPath().toFile()
    val first = File(topDir, "first/.mvn")
    val second = File(topDir, "second/.mvn")
    assertTrue(first.mkdirs())
    assertTrue(second.mkdirs())
    val connectorFirst = MavenServerManager.getInstance().getConnector(project, first.absolutePath)
    ensureConnected(connectorFirst)
    val connectorSecond = MavenServerManager.getInstance().getConnector(project, second.absolutePath)
    assertSame(connectorFirst, connectorSecond)
    MavenServerManager.getInstance().shutdownConnector(connectorFirst, true)
    assertEmpty(MavenServerManager.getInstance().getAllConnectors())
  }

  private fun kill(connector: MavenServerConnector) {
    val support = ReflectionUtil.getField(MavenServerConnectorImpl::class.java, connector, RemoteProcessSupport::class.java, "mySupport")
    val heartbeat = ReflectionUtil.getField(RemoteProcessSupport::class.java, support, AtomicReference::class.java, "myHeartbeatRef")
    (heartbeat.get() as RemoteProcessSupport.Heartbeat).kill(1)
    object : WaitFor(10000) {
      override fun condition(): Boolean {
        return !connector.checkConnected()
      }
    }
    assertFalse(connector.checkConnected())
  }
}
