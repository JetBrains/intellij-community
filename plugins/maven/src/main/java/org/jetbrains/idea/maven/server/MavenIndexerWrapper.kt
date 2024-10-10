// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.indices.MavenIndices
import org.jetbrains.idea.maven.model.MavenArtifactInfo
import org.jetbrains.idea.maven.model.MavenIndexId
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.io.File
import java.rmi.RemoteException
import java.rmi.server.UnicastRemoteObject

abstract class MavenIndexerWrapper : MavenRemoteObjectWrapper<MavenServerIndexer>() {
  @ApiStatus.Internal
  @Deprecated("use suspend", ReplaceWith("create"))
  @Throws(RemoteException::class)
  protected abstract fun createBlocking(): MavenServerIndexer

  @ApiStatus.Internal
  @Deprecated("use suspend", ReplaceWith("create"))
  @Throws(RemoteException::class)
  @Synchronized
  protected open fun getOrCreateWrappeeBlocking(): MavenServerIndexer {
    if (myWrappee == null) {
      myWrappee = createBlocking()
    }
    return myWrappee!!
  }

  fun startIndexing(info: MavenRepositoryInfo?, indexDir: File?): MavenIndexUpdateState? {
    try {
      val w = getOrCreateWrappeeBlocking()
      if (w !is AsyncMavenServerIndexer) {
        MavenLog.LOG.warn("wrappee not an instance of AsyncMavenServerIndexer, is dedicated indexer enabled?")
        return null
      }
      return w.startIndexing(info, indexDir, ourToken)
    }
    catch (e: RemoteException) {
      handleRemoteError(e)
    }
    return null
  }

  fun stopIndexing(info: MavenRepositoryInfo?) {
    try {
      val w = getOrCreateWrappeeBlocking()
      if (w !is AsyncMavenServerIndexer) {
        MavenLog.LOG.warn("wrappee not an instance of AsyncMavenServerIndexer, is dedicated indexer enabled?")
        return
      }
      w.stopIndexing(info, ourToken)
    }
    catch (e: RemoteException) {
      handleRemoteError(e)
    }
  }

  fun status(): List<MavenIndexUpdateState> {
    try {
      val w = getOrCreateWrappeeBlocking()
      if (w !is AsyncMavenServerIndexer) {
        MavenLog.LOG.warn("wrappee not an instance of AsyncMavenServerIndexer, is dedicated indexer enabled?")
        return emptyList()
      }
      return w.status(ourToken)
    }
    catch (e: RemoteException) {
      handleRemoteError(e)
    }
    return emptyList()
  }

  @Throws(MavenServerIndexerException::class)
  fun releaseIndex(mavenIndexId: MavenIndexId) {
    MavenLog.LOG.debug("releaseIndex " + mavenIndexId.indexId)

    val w = wrappee
    if (w == null) return

    try {
      w.releaseIndex(mavenIndexId, ourToken)
    }
    catch (e: RemoteException) {
      handleRemoteError(e)
    }
  }

  fun indexExists(dir: File?): Boolean {
    try {
      return getOrCreateWrappeeBlocking().indexExists(dir, ourToken)
    }
    catch (e: RemoteException) {
      handleRemoteError(e)
    }
    return false
  }

  val indexCount: Int
    get() = perform<Int, Exception> { getOrCreateWrappeeBlocking().getIndexCount(ourToken) }

  @Throws(MavenProcessCanceledException::class, MavenServerIndexerException::class)
  fun updateIndex(mavenIndexId: MavenIndexId,
                  indicator: MavenProgressIndicator,
                  multithreaded: Boolean) {
    performCancelable<Any, Exception>(
      indicator,
      RetriableCancelable<Any, Exception> {
        val indicatorWrapper = wrapAndExport(indicator)
        try {
          getOrCreateWrappeeBlocking().updateIndex(mavenIndexId, indicatorWrapper, multithreaded, ourToken)
        }
        finally {
          UnicastRemoteObject.unexportObject(indicatorWrapper, true)
        }
      })
  }

  @Throws(MavenServerIndexerException::class)
  fun processArtifacts(mavenIndexId: MavenIndexId?, processor: MavenIndicesProcessor, progress: MavenProgressIndicator) {
    perform<Any, Exception> {
      try {
        var start = 0
        var list: List<IndexedMavenId?>?
        do {
          if (progress.isCanceled) return@perform
          MavenLog.LOG.debug("process artifacts: $start")
          list = getOrCreateWrappeeBlocking().processArtifacts(mavenIndexId, start, ourToken)
          if (list != null) {
            processor.processArtifacts(list)
            start += list.size
          }
        }
        while (list != null)
        return@perform
      }
      catch (e: Exception) {
        return@perform
      }
    }
  }

  fun addArtifacts(mavenIndexId: MavenIndexId, artifactFiles: Collection<File>): List<AddArtifactResponse> {
    return perform<List<AddArtifactResponse>, Exception> {
      try {
        return@perform getOrCreateWrappeeBlocking().addArtifacts(mavenIndexId, ArrayList<File>(artifactFiles), ourToken)
      }
      catch (ignore: Throwable) {
        return@perform artifactFiles.map { file: File? -> AddArtifactResponse(file, null) }
      }
    }
  }

  @Throws(MavenServerIndexerException::class)
  fun search(mavenIndexId: MavenIndexId?, pattern: String?, maxResult: Int): Set<MavenArtifactInfo> {
    return perform<HashSet<MavenArtifactInfo>, Exception> { getOrCreateWrappeeBlocking().search(mavenIndexId, pattern, maxResult, ourToken) }
  }

  @ApiStatus.Internal
  fun getOrCreateIndices(project: Project): MavenIndices {
    return createMavenIndices(project)
  }

  protected abstract fun createMavenIndices(project: Project): MavenIndices
}

