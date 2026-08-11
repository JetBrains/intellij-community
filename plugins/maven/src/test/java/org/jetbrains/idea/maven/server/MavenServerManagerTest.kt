// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.execution.rmi.RemoteProcessSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.common.ThreadUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ReflectionUtil
import com.intellij.util.WaitFor
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class MavenServerManagerTest {
  private val maven by mavenImportingFixture()

  @Test
  fun testInitializingDoesntTakeReadAction() = runBlocking {
    //make sure all components are initialized to prevent deadlocks
    ensureConnected(MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path))

    val result = ApplicationManager.getApplication().runWriteAction(
      ThrowableComputable<Future<*>, Exception?> {
        ApplicationManager.getApplication().executeOnPooledThread {
          MavenServerManager.getInstance().closeAllConnectorsAndWait()
          runBlockingMaybeCancellable {
            ensureConnected(MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path))
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
      fail("Maven connector did not connect within timeout")
    }
    result.cancel(true)
    Unit
  }

  @Test
  fun testConnectorRestartAfterVMChanged() = runBlocking {
    val settingsComponent = MavenWorkspaceSettingsComponent.getInstance(maven.project)
    val vmOptions = settingsComponent.settings.importingSettings.vmOptionsForImporter
    try {
      val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
      ensureConnected(connector)
      settingsComponent.settings.importingSettings.vmOptionsForImporter = "$vmOptions -DtestVm=test"
      assertNotSame(connector, ensureConnected(MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)))
    }
    finally {
      settingsComponent.settings.importingSettings.vmOptionsForImporter = vmOptions
    }
  }

  @Test
  fun testShouldRestartConnectorAutomaticallyIfFailed() = runBlocking {
    val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
    ensureConnected(connector)
    kill(connector)
    val newConnector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
    ensureConnected(newConnector)
    assertNotSame(connector, newConnector)
  }


  @Test
  fun testShouldStopPullingIfConnectorIsFailing() = runBlocking {
    val connector = MavenServerManager.getInstance().getConnector(maven.project, maven.projectRoot.path)
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

  @Test
  fun testShouldDropConnectorForMultiplyDirs() = runBlocking {
    val topDir = maven.projectRoot.toNioPath()
    val first = topDir.resolve("first/.mvn")
    val second = topDir.resolve("second/.mvn")
    first.createDirectories()
    second.createDirectories()
    val connectorFirst = MavenServerManager.getInstance().getConnector(maven.project, first.toCanonicalPath())
    ensureConnected(connectorFirst)
    val connectorSecond = MavenServerManager.getInstance().getConnector(maven.project, second.toCanonicalPath())
    assertSame(connectorFirst, connectorSecond)
    MavenServerManager.getInstance().shutdownConnector(connectorFirst, true)
    assertEmpty(MavenServerManager.getInstance().getAllConnectors())
  }

  private fun ensureConnected(connector: MavenServerConnector): MavenServerConnector {
    assertTrue(connector is MavenServerConnectorImpl, "Connector is Dummy!")
    val timeout = TimeUnit.SECONDS.toMillis(10)
    val start = System.currentTimeMillis()
    while (connector.state == MavenServerConnector.State.STARTING) {
      if (System.currentTimeMillis() > start + timeout) {
        throw RuntimeException("Server connector not connected in 10 seconds")
      }
      EdtTestUtil.runInEdtAndWait<RuntimeException> {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      }
    }
    assertTrue(connector.checkConnected())
    return connector
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
