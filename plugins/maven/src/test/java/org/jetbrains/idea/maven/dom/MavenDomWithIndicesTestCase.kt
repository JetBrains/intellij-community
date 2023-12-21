/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.indices.MavenIndicesManager.MavenIndexerListener
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture
import org.jetbrains.idea.maven.onlinecompletion.MavenCompletionProviderFactory
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerDownloadListener
import org.jetbrains.idea.reposearch.DependencySearchService
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class MavenDomWithIndicesTestCase : MavenDomTestCase() {
  protected var myIndicesFixture: MavenIndicesTestFixture? = null
  override fun setUp() = runBlocking {
    super.setUp()
    maskExtensions(DependencySearchService.EP_NAME,
                   listOf(MavenCompletionProviderFactory()),
                   getTestRootDisposable(), false, null)
    myIndicesFixture = createIndicesFixture()
    myIndicesFixture!!.setUpBeforeImport()

    if (importProjectOnSetup()) {
      importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      """.trimIndent())
    }
    ApplicationManager.getApplication().invokeAndWait { myIndicesFixture!!.setUpAfterImport() }
  }

  protected open fun importProjectOnSetup(): Boolean {
    return false
  }

  protected open fun createIndicesFixture(): MavenIndicesTestFixture {
    return MavenIndicesTestFixture(dir.toPath(), project)
  }
  override fun tearDown() {
    try {
      if (myIndicesFixture != null) {
        myIndicesFixture!!.tearDown()
        myIndicesFixture = null
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  suspend protected fun runAndExpectPluginIndexEvents(expectedArtifactIds: Set<String>?, action: suspend () -> Unit) {
    val latch = CountDownLatch(1)
    val artifactIdsToIndex: MutableSet<String> = ConcurrentHashMap.newKeySet()
    artifactIdsToIndex.addAll(expectedArtifactIds!!)

    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(MavenIndicesManager.INDEXER_TOPIC, MavenIndexerListener { added, failedToAdd ->
        artifactIdsToIndex.removeIf { artifactId: String? ->
          ContainerUtil.exists(added) { file: File ->
            file.path.contains(
              artifactId!!)
          }
        }
        if (artifactIdsToIndex.isEmpty()) {
          latch.countDown()
        }
      })

    action()

    try {
      latch.await(1, TimeUnit.MINUTES)
    }
    catch (e: InterruptedException) {
      throw RuntimeException(e)
    }

    assertTrue("Maven plugins are not indexed in time: " + java.lang.String.join(", ", artifactIdsToIndex), artifactIdsToIndex.isEmpty())
  }

  protected suspend fun runAndExpectArtifactDownloadEvents(groupId: String, artifactIds: Set<String>, action: suspend () -> Unit) {
    val groupFolder = groupId.replace('.', '/')
    val latch = CountDownLatch(1)
    val actualEvents: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val downloadListener = MavenServerDownloadListener { file, relativePath ->
      if (relativePath.startsWith(groupFolder)) {
        val artifactId = relativePath.substring(groupFolder.length).split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
        if (artifactIds.contains(artifactId)) {
          actualEvents.add(artifactId)
          if (actualEvents.size == artifactIds.size) {
            latch.countDown()
          }
        }
      }
    }

    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(MavenServerConnector.DOWNLOAD_LISTENER_TOPIC, downloadListener)

    action()

    try {
      latch.await(1, TimeUnit.MINUTES)
    }
    catch (e: InterruptedException) {
      throw RuntimeException(e)
    }

    assertUnorderedElementsAreEqual(artifactIds, actualEvents)
  }
}