// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.entities.modifyExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuild
import com.intellij.python.pyproject.model.internal.platformBridge.toRebuildRequest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.createDirectories
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/**
 * Proves that a relocated exclusion starts no model build (PY-91841).
 *
 * `ensureNoSrcIntersectsWithOtherRoots` moves an excluded url from one content root to another. Such a move
 * woke the tracker, and the model then built a second time for nothing. The state that produces the move
 * needs an orphan module, so it does not repeat on demand in an IDE. This test therefore builds the same
 * workspace model event and gives it to the decision of the tracker.
 */
@TestApplication
@Subsystems.IDE
@Layers.Functional
@Timeout(30)
internal class PyWsmTriggerTest {
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)

  private val source = NonPersistentEntitySource

  private lateinit var workspaceModel: WorkspaceModel
  private lateinit var excluded: VirtualFileUrl

  /** Two modules, each with one content root. The first content root excludes one directory. */
  @BeforeEach
  fun createTwoModules(): Unit = timeoutRunBlocking {
    val root: Path = pathFixture.get()
    workspaceModel = projectFixture.get().workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    fun urlOf(name: String): VirtualFileUrl =
      urlManager.getOrCreateFromUrl("file://" + root.resolve(name).createDirectories())
    val first = urlOf("first")
    val second = urlOf("second")
    excluded = urlOf("first/out")

    workspaceModel.update("test setup") { storage ->
      storage addEntity ModuleEntity("first", emptyList(), source) {
        contentRoots = listOf(ContentRootEntity(first, emptyList(), source) {
          excludedUrls = listOf(ExcludeUrlEntity(excluded, source))
        })
      }
      storage addEntity ModuleEntity("second", emptyList(), source) {
        contentRoots = listOf(ContentRootEntity(second, emptyList(), source))
      }
    }
  }

  @Test
  fun testARelocatedExclusionStartsNoBuild(): Unit = timeoutRunBlocking {
    val event = captureEvent {
      // What a rebuild does at `workspaceTools.kt:282`. It removes an orphan module, which removes the
      // excluded urls of that module, and `addRelocatedRoots` adds the same urls to the surviving content
      // root. That path writes no 'Relocating roots' line.
      workspaceModel.update("remove a module and relocate its exclusion") { storage ->
        storage.removeEntity(storage.entities<ModuleEntity>().single { it.name == "first" })
        storage.modifyContentRootEntity(storage.contentRootOf("second")) {
          excludedUrls = listOf(ExcludeUrlEntity(excluded, source))
        }
      }
    }

    // The event must really be a pair, or the test would pass for the wrong reason.
    assertThat(event.removedExcludeUrls()).describedAs("the event must remove the url").containsExactly(excluded.url)
    assertThat(event.addedExcludeUrls()).describedAs("the event must add the url again").containsExactly(excluded.url)

    assertThat(event.toRebuildRequest(pathFixture.get()))
      .describedAs("a relocated exclusion leaves the set of excluded paths equal, so it must start no build")
      .isNull()
  }

  @Test
  fun testARealUnExclusionStartsABuild(): Unit = timeoutRunBlocking {
    val event = captureEvent {
      workspaceModel.update("drop the exclusion") { storage ->
        storage.removeEntity(storage.contentRootOf("first").excludedUrls.single())
      }
    }

    assertThat(event.removedExcludeUrls()).containsExactly(excluded.url)
    assertThat(event.addedExcludeUrls()).describedAs("nothing adds the url back").isEmpty()

    val request = event.toRebuildRequest(pathFixture.get())
    assertThat(request).describedAs("a real un-exclusion must start a build").isNotNull()
    assertThat(request!!.reason).contains("no longer excluded")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testReplacingAnExclusionRebuildsOnlyWhenItsUrlChanges(changeUrl: Boolean): Unit = timeoutRunBlocking {
    val oldDirectory = checkNotNull(
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(pathFixture.get().resolve("first/out"))
    )
    val newUrl = workspaceModel.getVirtualFileUrlManager()
      .getOrCreateFromUrl("file://" + pathFixture.get().resolve("second/out").createDirectories())
    val event = captureEvent {
      workspaceModel.update("replace the exclusion") { storage ->
        storage.modifyExcludeUrlEntity(storage.contentRootOf("first").excludedUrls.single()) {
          if (changeUrl) url = newUrl
          else entitySource = OrphanageWorkerEntitySource
        }
      }
    }

    val replacement = event.getChanges(ExcludeUrlEntity::class.java).single()
    assertInstanceOf(EntityChange.Replaced::class.java, replacement)
    assertThat(replacement.oldEntity!!.url).isEqualTo(excluded)
    assertThat(replacement.newEntity!!.url).isEqualTo(if (changeUrl) newUrl else excluded)
    val request = event.toRebuildRequest(pathFixture.get())
    if (changeUrl) {
      assertThat(request).isNotNull()
      assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, request).directoriesToLoad).containsExactly(oldDirectory)
      assertThat(request!!.reason).contains("no longer excluded", excluded.url)
    }
    else {
      assertThat(request).isNull()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["add", "replace", "remove"])
  fun testExternalRootChangesRequestAFullScan(change: String, @TempDir externalRoot: Path): Unit = timeoutRunBlocking {
    val externalUrl = workspaceModel.getVirtualFileUrlManager().getOrCreateFromUrl("file://$externalRoot")
    if (change == "remove") {
      workspaceModel.update("add the external root") { storage ->
        storage.modifyContentRootEntity(storage.contentRootOf("second")) { url = externalUrl }
      }
    }
    val event = captureEvent {
      workspaceModel.update("change the external root") { storage ->
        when (change) {
          "add" -> storage.modifyModuleEntity(storage.entities<ModuleEntity>().single { it.name == "second" }) {
            contentRoots = contentRoots + ContentRootEntity(externalUrl, emptyList(), source)
          }
          "replace" -> storage.modifyContentRootEntity(storage.contentRootOf("second")) { url = externalUrl }
          "remove" -> storage.removeEntity(storage.contentRootOf("second"))
          else -> error("Unknown change: $change")
        }
      }
    }
    assertInstanceOf(PendingRebuild.FullScan::class.java, event.toRebuildRequest(pathFixture.get()))
  }

  @Test
  fun testRootWithinTheProjectNeedsNoScan(): Unit = timeoutRunBlocking {
    val root = workspaceModel.getVirtualFileUrlManager().getOrCreateFromUrl("file://" + pathFixture.get().resolve("nested"))
    val event = captureEvent {
      workspaceModel.update("add a nested content root") { storage ->
        storage.modifyModuleEntity(storage.entities<ModuleEntity>().single { it.name == "second" }) {
          contentRoots = contentRoots + ContentRootEntity(root, emptyList(), source)
        }
      }
    }
    assertThat(event.toRebuildRequest(pathFixture.get())).isNull()
  }

  @Test
  fun testRelocatingAnExternalRootNeedsNoScan(@TempDir externalRoot: Path): Unit = timeoutRunBlocking {
    val externalUrl = workspaceModel.getVirtualFileUrlManager().getOrCreateFromUrl("file://$externalRoot")
    workspaceModel.update("add the external root") { storage ->
      storage.modifyContentRootEntity(storage.contentRootOf("second")) { url = externalUrl }
    }
    val event = captureEvent {
      workspaceModel.update("move the external root to another module") { storage ->
        storage.removeEntity(storage.contentRootOf("second"))
        storage.modifyModuleEntity(storage.entities<ModuleEntity>().single { it.name == "first" }) {
          contentRoots = contentRoots + ContentRootEntity(externalUrl, emptyList(), source)
        }
      }
    }
    assertThat(event.toRebuildRequest(pathFixture.get())).isNull()
  }

  @Test
  fun testRenamingAPlatformModuleRequestsARebuild(): Unit = timeoutRunBlocking {
    val event = captureEvent {
      workspaceModel.update("rename the platform module") { storage ->
        storage.modifyModuleEntity(storage.entities<ModuleEntity>().single { it.name == "second" }) { name = "renamed" }
      }
    }
    val request = assertInstanceOf(PendingRebuild.Directories::class.java, event.toRebuildRequest(pathFixture.get()))
    assertThat(request.directoriesToLoad).isEmpty()
    assertThat(request.reason).contains("renamed")
  }

  private fun com.intellij.platform.workspace.storage.MutableEntityStorage.contentRootOf(module: String): ContentRootEntity =
    entities<ModuleEntity>().single { it.name == module }.contentRoots.single()

  /**
   * Runs [change] and returns the event for that workspace update.
   *
   * A project emits an event of its own, so the event of [change] must be selected and not taken first.
   */
  private suspend fun CoroutineScope.captureEvent(change: suspend () -> Unit): VersionedStorageChange {
    val before = workspaceModel.currentSnapshot
    val event = async(start = CoroutineStart.UNDISPATCHED) {
      workspaceModel.eventLog.first {
        it.storageBefore === before
      }
    }
    try {
      change()
      return event.await()
    }
    finally {
      event.cancel()
    }
  }
}

private fun VersionedStorageChange.addedExcludeUrls(): List<String> =
  getChanges(ExcludeUrlEntity::class.java).mapNotNull {
    when (it) {
      is EntityChange.Added -> it.newEntity.url.url
      is EntityChange.Removed, is EntityChange.Replaced -> null
    }
  }

private fun VersionedStorageChange.removedExcludeUrls(): List<String> =
  getChanges(ExcludeUrlEntity::class.java).mapNotNull {
    when (it) {
      is EntityChange.Removed -> it.oldEntity.url.url
      is EntityChange.Added, is EntityChange.Replaced -> null
    }
  }
