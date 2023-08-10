// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.mapSmartNotNull
import kotlinx.coroutines.ensureActive
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.jetbrains.settingsRepository.git.*
import java.nio.file.Path
import kotlin.coroutines.coroutineContext
import kotlin.io.path.exists

class ReadOnlySourceManager(private val icsManager: IcsManager, val rootDir: Path) {
  private val repositoryList = SynchronizedClearableLazy {
    if (icsManager.settings.readOnlySources.isEmpty()) {
      return@SynchronizedClearableLazy emptyList()
    }

    icsManager.settings.readOnlySources.mapSmartNotNull { source ->
      runCatching {
        if (!source.active) {
          return@runCatching null
        }

        val path = source.path ?: return@mapSmartNotNull null
        val dir = rootDir.resolve(path)
        if (dir.exists()) {
          return@runCatching buildBareRepository(dir)
        }
        else {
          LOG.warn("Skip read-only source ${source.url} because dir doesn't exist")
        }
        null
      }.getOrLogException(LOG)
    }
  }

  val repositories: List<Repository>
    get() = repositoryList.value

  fun setSources(sources: List<ReadonlySource>) {
    icsManager.settings.readOnlySources = sources
    repositoryList.drop()
  }

  suspend fun update(): Set<String>? {
    var changedRootDirs: MutableSet<String>? = null

    fun addChangedPath(path: String?) {
      if (path == null || path == DiffEntry.DEV_NULL) {
        return
      }

      var firstSlash = path.indexOf('/')
      if (firstSlash < 0) {
        // path must use only /, but who knows
        firstSlash = path.indexOf('\\')
      }

      if (firstSlash > 0) {
        if (changedRootDirs == null) {
          changedRootDirs = CollectionFactory.createSmallMemoryFootprintSet()
        }

        changedRootDirs!!.add(path.substring(0, firstSlash))
      }
    }

    for (repo in repositories) {
      coroutineContext.ensureActive()
      LOG.debug { "Pull changes from read-only repo ${repo.upstream}" }

      Pull(GitRepositoryClientImpl(repo, icsManager.credentialsStore)).fetch(refUpdateProcessor = { refUpdate ->
        val diffFormatter = DiffFormatter(DisabledOutputStream.INSTANCE)
        diffFormatter.setRepository(repo)
        diffFormatter.use {
          val result = diffFormatter.scan(refUpdate.oldObjectId, refUpdate.newObjectId)
          for (e in result) {
            if (e.changeType == DiffEntry.ChangeType.DELETE) {
              addChangedPath(e.oldPath)
            }
            else {
              addChangedPath(e.oldPath)
              addChangedPath(e.newPath)
            }
          }
        }
      })
    }
    return changedRootDirs
  }
}