// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.PathUtilRt
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.server.MavenIndexerWrapper
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

@Service
class MavenSystemIndicesManager(val cs: CoroutineScope) {
  private val openedIndices = HashMap<String, MavenIndex>()
  private val updatingIndices = HashMap<String, Deferred<MavenIndex>>()
  private val mutex = Mutex()

  private var ourTestIndicesDir: Path? = null
  suspend fun getClassIndexForRepository(repo: MavenRepositoryInfo): MavenSearchIndex {
    return getIndexForRepo(repo)
  }

  suspend fun getGAVIndexForRepository(repo: MavenRepositoryInfo): MavenGAVIndex {
    return getIndexForRepo(repo)
  }

  @TestOnly
  fun setTestIndicesDir(myTestIndicesDir: Path?) {
    ourTestIndicesDir = myTestIndicesDir
  }

  fun getIndicesDir(): Path {
    return ourTestIndicesDir ?: MavenUtil.getPluginSystemDir("Indices")
  }

  fun getIndexForRepoSync(repo: MavenRepositoryInfo): MavenIndex {
    return runBlockingMaybeCancellable {
      getIndexForRepo(repo)
    }
  }

  fun updateIndexContentSync(repo: MavenRepositoryInfo,
                             fullUpdate: Boolean,
                             multithreaded: Boolean,
                             indicator: MavenProgressIndicator) {
    return runBlockingMaybeCancellable {
      updateIndexContent(repo, fullUpdate, multithreaded, indicator)
    }
  }

  private suspend fun updateIndexContent(repo: MavenRepositoryInfo,
                                         fullUpdate: Boolean,
                                         multithreaded: Boolean,
                                         indicator: MavenProgressIndicator) {

    coroutineScope {

      val updateScope = this
      val connection = ApplicationManager.getApplication().messageBus.connect(updateScope)
      connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appClosing() {
          updateScope.cancel()
          indicator.cancel()
          MavenLog.LOG.info("Application is closing, gracefully shutdown all indexing operations")
        }
      })

      indicator.addCancelCondition {
        !updateScope.isActive
      }

      val index = getIndexForRepo(repo)
      val deferredResult = mutex.withLock {
        val deferred = updatingIndices[repo.url]
        if (deferred == null) {
          val newDeferred = updateScope.async {
            index.updateOrRepair(fullUpdate, indicator, multithreaded)
            index
          }
          updatingIndices.putIfAbsent(repo.url, newDeferred)
          return@withLock newDeferred
        }
        else return@withLock deferred
      }
      deferredResult.invokeOnCompletion {
        updateScope.async {
          mutex.withLock {
            updatingIndices.remove(repo.url)
          }
        }
      }

      deferredResult.await()
    }

  }

  private suspend fun getIndexForRepo(repo: MavenRepositoryInfo): MavenIndex {
    return cs.async(Dispatchers.IO) {
      val dir = getDirForMavenIndex(repo)
      mutex.withLock {
        openedIndices[dir.toString()]?.let { return@async it }

        val holder = getProperties(dir) ?: MavenIndexUtils.IndexPropertyHolder(
          dir.toFile(),
          repo.kind,
          setOf(repo.id),
          repo.url
        )
        return@async MavenIndexImpl(getIndexWrapper(), holder).also { openedIndices[dir.toString()] = it }
      }
    }.await()
  }

  private fun getProperties(dir: Path): MavenIndexUtils.IndexPropertyHolder? {
    try {
      return MavenIndexUtils.readIndexProperty(dir.toFile())
    }
    catch (e: MavenIndexException) {
      MavenLog.LOG.warn(e)
      return null
    }

  }

  private fun getIndexWrapper(): MavenIndexerWrapper {
    return MavenServerManager.getInstance().createIndexer()
  }

  private fun getDirForMavenIndex(repo: MavenRepositoryInfo): Path {
    val url = getCanonicalUrl(repo)
    val key = PathUtilRt.suggestFileName(PathUtilRt.getFileName(url), false, false)

    val locationHash = Integer.toHexString((url).hashCode())
    return getIndicesDir().resolve("$key-$locationHash")
  }

  private fun getCanonicalUrl(repo: MavenRepositoryInfo): String {
    if (File(repo.url).isDirectory) return File(repo.url).canonicalPath
    try {
      val uri = URI(repo.url)
      if (uri.scheme == null || uri.scheme.lowercase() == "file") {
        val path = uri.path
        if (path != null) return path
      }
      return uri.toString()
    }
    catch (e: URISyntaxException) {
      return repo.url
    }

  }

  fun getOrCreateIndices(project: Project): MavenIndices {
    return getIndexWrapper().getOrCreateIndices(project)
  }

  fun getUpdatingStateSync(project: Project, repository: MavenRepositoryInfo): MavenIndexUpdateManager.IndexUpdatingState {
    return runWithModalProgressBlocking(project, repository.name) {
      return@runWithModalProgressBlocking mutex.withLock {
        val deferred = updatingIndices[repository.url]
        if (deferred == null) return@withLock MavenIndexUpdateManager.IndexUpdatingState.IDLE
        return@withLock MavenIndexUpdateManager.IndexUpdatingState.UPDATING
      }
    }
  }

  companion object {

    @JvmStatic
    fun getInstance(): MavenSystemIndicesManager = ApplicationManager.getApplication().service()
  }
}

