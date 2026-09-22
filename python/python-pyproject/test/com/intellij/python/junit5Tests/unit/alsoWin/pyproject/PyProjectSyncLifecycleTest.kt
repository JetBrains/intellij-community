// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.api.ModelRebuiltListener
import com.intellij.python.pyproject.model.api.isPyProjectTomlBased
import com.intellij.python.pyproject.model.internal.MODEL_REBUILD
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuild
import com.intellij.python.pyproject.model.internal.platformBridge.PyProjectModelSyncService
import com.intellij.python.pyproject.model.internal.platformBridge.collectRebuilds
import com.intellij.python.pyproject.model.internal.platformBridge.loadSubtreesIntoVfs
import com.intellij.python.pyproject.model.internal.platformBridge.toRebuildRequest
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.apache.tuweni.toml.TomlTable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

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
    val rebuilt = CompletableDeferred<Pair<Int, List<AsyncFileListener>>>()
    project.messageBus.connect(disposable).subscribe(MODEL_REBUILD, ModelRebuiltListener {
      rebuilt.complete(workspace.subscriptions.value to vfsListeners())
    })
    val service = PyProjectModelSyncService(project, this)
    try {
      service.start()
      workspace.waitStarted.await()
      val nested = pathFixture.get().resolve("new-package/nested").createDirectories()
      nested.resolve(PY_PROJECT_TOML).writeText("[project]\nname = \"nested\"\nversion = \"1.0\"\n")
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(pathFixture.get())!!.refresh(false, false)
      workspace.ready.complete(Unit)
      val (activeSubscriptions, activeListeners) = rebuilt.await()
      assertEquals(listOf("nested"), project.modules.filter { it.isPyProjectTomlBased }.map { it.name })
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
  fun testWorkspaceSubscriptionFollowsCollection(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val workspace = gateWorkspace(disposable)
    val requests = projectFixture.get().workspaceModel.eventLog.mapNotNull { it.toRebuildRequest() }
    assertEquals(0, workspace.subscriptions.value)
    val tracker = launch(start = CoroutineStart.UNDISPATCHED) {
      requests.collect()
    }
    try {
      assertEquals(1, workspace.subscriptions.value)
    }
    finally {
      tracker.cancelAndJoin()
    }
    assertEquals(0, workspace.subscriptions.value)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testRecoveryKeepsSubscriptionsAndReportsTheFailure(
    onStart: Boolean,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val workspace = gateWorkspace(disposable)
    val root = pathFixture.get()
    val member = root.resolve("member").createDirectories().resolve(PY_PROJECT_TOML)
    member.writeText("[project]\nname = \"member\"\nversion = \"1.0\"\n")
    val failure = IOException("Cannot read the source roots")
    val failNext = AtomicBoolean(onStart)
    val managers = PyProjectManager.EP.extensionList
    val manager = managers.first()
    val failingManager = object : PyProjectManager by manager {
      override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> {
        if (failNext.compareAndSet(true, false)) throw failure
        return manager.getSrcRoots(toml, projectRoot)
      }
    }
    ExtensionTestUtil.maskExtensions(PyProjectManager.EP, listOf(failingManager) + managers.drop(1), disposable)

    val reports = AtomicInteger()
    val reported = CompletableDeferred<Unit>()
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        if (generateSequence(t) { it.cause }.any { it === failure }) {
          reports.incrementAndGet()
          reported.complete(Unit)
          return Action.NONE
        }
        return super.processError(category, message, details, t)
      }
    }
    val project = projectFixture.get()
    val rebuilt = Channel<List<String>>(Channel.UNLIMITED)
    project.messageBus.connect(disposable).subscribe(MODEL_REBUILD, ModelRebuiltListener {
      rebuilt.trySend(project.modules.filter { it.isPyProjectTomlBased }.map { it.name }.sorted())
    })
    suspend fun awaitModules(vararg names: String) {
      val expected = names.sorted()
      while (rebuilt.receive() != expected) { }
      assertEquals(expected, project.modules.filter { it.isPyProjectTomlBased }.map { it.name }.sorted())
    }
    val service = PyProjectModelSyncService(project, this)
    LoggedErrorProcessor.executeWith(processor).use {
      try {
        workspace.ready.complete(Unit)
        service.start()
        if (!onStart) {
          awaitModules("member")
          failNext.set(true)
          member.writeText("[project]\nname = \"changed\"\nversion = \"1.0\"\n")
          VirtualFileManager.getInstance().refreshAndFindFileByNioPath(member.parent)!!.refresh(false, false)
        }
        reported.await()
        assertThat(service.initialized).isTrue()
        val name = if (onStart) "member" else "changed"
        awaitModules(name)

        root.resolve("second/nested").createDirectories().resolve(PY_PROJECT_TOML)
          .writeText("[project]\nname = \"second\"\nversion = \"1.0\"\n")
        VirtualFileManager.getInstance().refreshAndFindFileByNioPath(root)!!.refresh(false, false)
        awaitModules(name, "second")
        assertEquals(1, reports.get())
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
        rebuilt.cancel()
      }
    }
  }

  @Test
  fun testFailedSubtreeLoadIsRetriedBeforeDiscovery(): Unit = timeoutRunBlocking {
    val root = pathFixture.get()
    val expected = root.resolve("fresh/nested").createDirectories().resolve(PY_PROJECT_TOML)
    expected.writeText("[project]\nname = \"nested\"\nversion = \"1.0\"\n")
    val rootFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(root)!!
    val failNext = AtomicBoolean(true)
    val failure = IOException("Cannot enumerate the subtree")
    val directory = object : MockVirtualFile(true, rootFile.name) {
      override fun toNioPath(): Path = root

      override fun getChildren(): Array<VirtualFile> {
        if (failNext.compareAndSet(true, false)) throw failure
        return rootFile.children
      }
    }
    assertThat(findPyProjectTomlFilesInIndex(setOf(root), emptySet())).isEmpty()
    val reports = mutableListOf<Exception>()
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        if (generateSequence(t) { it.cause }.any { it === failure }) {
          reports.add(failure)
          return Action.NONE
        }
        return super.processError(category, message, details, t)
      }
    }
    var found: List<Path> = emptyList()
    var attempts = 0
    LoggedErrorProcessor.executeWith(processor).use {
      flowOf(PendingRebuild.Directories(setOf(directory), "new subtree"))
        .collectRebuilds(listOf(10.milliseconds)) { batch ->
          attempts++
          loadSubtreesIntoVfs(assertInstanceOf(PendingRebuild.Directories::class.java, batch).directoriesToLoad)
          found = findPyProjectTomlFilesInIndex(setOf(root), emptySet())
        }
    }
    assertEquals(2, attempts)
    assertThat(reports).hasSize(1)
    assertThat(found).containsExactly(expected)
  }

  private fun gateWorkspace(disposable: Disposable): GatedWorkspace {
    val project = projectFixture.get()
    project.service<InitialVfsRefreshService>().scheduleInitialVfsRefresh()
    val workspace = GatedWorkspace(project.workspaceModel as WorkspaceModelInternal)
    project.replaceService(WorkspaceModel::class.java, workspace, disposable)
    return workspace
  }

  private fun vfsListeners(): List<AsyncFileListener> {
    val manager = VirtualFileManager.getInstance() as VirtualFileManagerImpl
    return manager.withAsyncFileListenersBackgroundable(emptyList())
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
