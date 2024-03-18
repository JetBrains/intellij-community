// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.*
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.junit.Test
import java.io.File

class StructureImportingFsRefreshTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testRefreshFSAfterImport() = runBlocking {
    val fm = VirtualFileManager.getInstance()
    val vfsRefreshPromise = AsyncPromise<Any?>()
    val mockFm = MockVirtualFileManager(fm, vfsRefreshPromise)
    withMockVirtualFileManager(mockFm) {
      projectRoot.children // make sure fs is cached
      File(projectRoot.path, "foo").mkdirs()
      importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
      withContext(Dispatchers.EDT) {
        PlatformTestUtil.waitForPromise(vfsRefreshPromise)
      }
      assertNotNull(projectRoot.findChild("foo"))
    }
  }

  private suspend fun <R> withMockVirtualFileManager(mockFm: MockVirtualFileManager, action: suspend () -> R): R {
    Disposer.newDisposable().use { disposable ->
      ApplicationManager.getApplication().replaceService(VirtualFileManager::class.java, mockFm, disposable)
      return action()
    }
  }

  private class MockVirtualFileManager(private val delegate: VirtualFileManager,
                                       private val vfsRefreshPromise: AsyncPromise<Any?>) : VirtualFileManager() {
    override fun getModificationCount() = delegate.modificationCount

    override fun getFileSystem(protocol: String?) = delegate.getFileSystem(protocol)

    override fun syncRefresh(): Long {
      val result = delegate.syncRefresh()
      vfsRefreshPromise.setResult(null)
      return result
    }

    override fun asyncRefresh(postAction: Runnable?): Long {
      return delegate.asyncRefresh {
        vfsRefreshPromise.setResult(null)
      }
    }

    override fun refreshWithoutFileWatcher(asynchronous: Boolean) = delegate.refreshWithoutFileWatcher(asynchronous)

    @Deprecated("Deprecated in Java")
    override fun addVirtualFileListener(listener: VirtualFileListener) = delegate.addVirtualFileListener(listener)

    @Deprecated("Deprecated in Java")
    override fun addVirtualFileListener(listener: VirtualFileListener, parentDisposable: Disposable) =
      delegate.addVirtualFileListener(listener, parentDisposable)

    @Deprecated("Deprecated in Java")
    override fun removeVirtualFileListener(listener: VirtualFileListener) = delegate.removeVirtualFileListener(listener)

    override fun addAsyncFileListener(listener: AsyncFileListener, parentDisposable: Disposable) =
      delegate.addAsyncFileListener(listener, parentDisposable)

    override fun addVirtualFileManagerListener(listener: VirtualFileManagerListener, parentDisposable: Disposable) =
      delegate.addVirtualFileManagerListener(listener, parentDisposable)

    @Deprecated("Deprecated in Java")
    override fun removeVirtualFileManagerListener(listener: VirtualFileManagerListener) =
      delegate.removeVirtualFileManagerListener(listener)

    override fun notifyPropertyChanged(virtualFile: VirtualFile, property: String, oldValue: Any?, newValue: Any?) =
      delegate.notifyPropertyChanged(virtualFile, property, oldValue, newValue)

    override fun getStructureModificationCount() = delegate.structureModificationCount

    override fun storeName(name: String) = delegate.storeName(name)

    override fun getVFileName(nameId: Int) = delegate.getVFileName(nameId)
  }
}
