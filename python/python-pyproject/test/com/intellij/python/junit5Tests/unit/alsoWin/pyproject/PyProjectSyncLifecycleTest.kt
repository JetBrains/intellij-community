// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.api.isPyProjectTomlBased
import com.intellij.python.pyproject.model.internal.MODEL_REBUILD
import com.intellij.python.pyproject.model.internal.platformBridge.PyProjectModelSyncService
import com.intellij.python.pyproject.model.internal.platformBridge.createWsmTracker
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TestApplication
@Timeout(30)
internal class PyProjectSyncLifecycleTest {
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)

  @Test
  fun testCancellationDuringStartupDoesNotSubscribe(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val workspace = gateWorkspace(disposable)
    val listeners = vfsListeners()
    val service = PyProjectModelSyncService(projectFixture.get(), this)
    try {
      service.start()
      workspace.waitStarted.await()
      assertEquals(listeners, vfsListeners())
      assertEquals(0, workspace.subscriptions.value)
      service.stop()
      coroutineContext.job.children.toList().joinAll()
      workspace.ready.complete(Unit)
      assertFalse(service.initialized)
      assertEquals(listeners, vfsListeners())
      assertEquals(0, workspace.subscriptions.value)
    }
    finally {
      service.stop()
      coroutineContext.job.children.toList().joinAll()
    }
  }

  @Test
  fun testInitialBuildIncludesChangesDuringStartup(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val workspace = gateWorkspace(disposable)
    val project = projectFixture.get()
    val rebuilt = CompletableDeferred<Unit>()
    project.messageBus.connect(disposable).subscribe(MODEL_REBUILD, ModelRebuiltListener { rebuilt.complete(Unit) })
    val service = PyProjectModelSyncService(project, this)
    try {
      service.start()
      workspace.waitStarted.await()
      val nested = pathFixture.get().resolve("new-package/nested").createDirectories()
      nested.resolve(PY_PROJECT_TOML).writeText("[project]\nname = \"nested\"\nversion = \"1.0\"\n")
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(pathFixture.get())!!.refresh(false, false)
      workspace.ready.complete(Unit)
      rebuilt.await()
      assertEquals(listOf("nested"), project.modules.filter { it.isPyProjectTomlBased }.map { it.name })
      val activeSubscriptions = workspace.subscriptions.value
      val activeListeners = vfsListeners()
      service.stop()
      coroutineContext.job.children.toList().joinAll()
      assertEquals(activeSubscriptions - 1, workspace.subscriptions.value)
      assertEquals(1, (activeListeners - vfsListeners().toSet()).size)
    }
    finally {
      service.stop()
      coroutineContext.job.children.toList().joinAll()
    }
  }

  @Test
  fun testWorkspaceSubscriptionIsReadyOnReturn(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val workspace = gateWorkspace(disposable)
    val tracker = createWsmTracker(projectFixture.get()) { _, _ -> }
    try {
      assertEquals(1, workspace.subscriptions.value)
    }
    finally {
      tracker.cancelAndJoin()
    }
    assertEquals(0, workspace.subscriptions.value)
  }

  private fun gateWorkspace(disposable: Disposable): GatedWorkspace {
    val project = projectFixture.get()
    project.service<InitialVfsRefreshService>().scheduleInitialVfsRefresh()
    val workspace = GatedWorkspace(project.workspaceModel as WorkspaceModelInternal)
    project.replaceService(WorkspaceModel::class.java, workspace, disposable)
    return workspace
  }

  private fun vfsListeners(): List<*> {
    val manager = VirtualFileManager.getInstance()
    return manager.javaClass.getMethod("withAsyncFileListenersBackgroundable", List::class.java)
      .invoke(manager, emptyList<Any>()) as List<*>
  }

  private class GatedWorkspace(delegate: WorkspaceModelInternal) : WorkspaceModelInternal by delegate {
    val waitStarted = CompletableDeferred<Unit>()
    val ready = CompletableDeferred<Unit>()
    val subscriptions = MutableStateFlow(0)
    override val eventLog = delegate.eventLog
      .onStart { subscriptions.update { it + 1 } }
      .onCompletion { subscriptions.update { it - 1 } }

    override suspend fun awaitSynchronizationWithJpsModel() {
      waitStarted.complete(Unit)
      ready.await()
    }
  }
}
