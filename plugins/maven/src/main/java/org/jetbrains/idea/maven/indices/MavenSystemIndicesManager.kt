// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtilRt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.server.MavenIndexerWrapper
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

@Service
class MavenSystemIndicesManager(val cs: CoroutineScope) {
  private val openedIndices = HashMap<String, MavenIndex>()
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
        return@async MavenIndex(getIndexWrapper(), holder).also { openedIndices[dir.toString()] = it }
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
    return MavenServerManager.getInstance().createIndexer();
  }

  private fun getDirForMavenIndex(repo: MavenRepositoryInfo): Path {
    val url = getCanonicalUrl(repo)
    val key = PathUtilRt.getFileName(url)
    val locationHash = Integer.toHexString((url).hashCode())
    return getIndicesDir().resolve("$key-$locationHash")
  }

  private fun getCanonicalUrl(repo: MavenRepositoryInfo): String {
    if (File(repo.url).isDirectory) return File(repo.url).canonicalPath
    try {
      val uri = URI(repo.url)
      if (uri.scheme.lowercase() == "file") return uri.path
      return repo.url
    }
    catch (e: URISyntaxException) {
      return repo.url
    }

  }

  fun getOrCreateIndices(project: Project): MavenIndices {
    return getIndexWrapper().getOrCreateIndices(project)
  }

  companion object {

    @JvmStatic
    fun getInstance(): MavenSystemIndicesManager = ApplicationManager.getApplication().service()
  }
}

