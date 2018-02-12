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
package org.jetbrains.settingsRepository

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.AtomicClearableLazyValue
import com.intellij.util.containers.mapSmartNotNull
import com.intellij.util.io.exists
import gnu.trove.THashSet
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.jetbrains.settingsRepository.git.*
import java.nio.file.Path

class ReadOnlySourceManager(private val icsManager: IcsManager, val rootDir: Path) {
  private val repositoryList = object : AtomicClearableLazyValue<List<Repository>>() {
    override fun compute(): List<Repository> {
      if (icsManager.settings.readOnlySources.isEmpty()) {
        return emptyList()
      }

      return icsManager.settings.readOnlySources.mapSmartNotNull { source ->
        LOG.runAndLogException {
          if (!source.active) {
            return@mapSmartNotNull null
          }

          val path = source.path ?: return@mapSmartNotNull null
          val dir = rootDir.resolve(path)
          if (dir.exists()) {
            return@mapSmartNotNull buildBareRepository(dir)
          }
          else {
            LOG.warn("Skip read-only source ${source.url} because dir doesn't exist")
          }
          null
        }
      }
    }
  }

  val repositories: List<Repository>
    get() = repositoryList.value

  fun setSources(sources: List<ReadonlySource>) {
    icsManager.settings.readOnlySources = sources
    repositoryList.drop()
  }

  fun update(indicator: ProgressIndicator? = null): Set<String>? {
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
          changedRootDirs = THashSet()
        }

        changedRootDirs!!.add(path.substring(0, firstSlash))
      }
    }

    for (repo in repositories) {
      indicator?.checkCanceled()
      LOG.debug { "Pull changes from read-only repo ${repo.upstream}" }

      Pull(GitRepositoryClientImpl(repo, icsManager.credentialsStore), indicator).fetch(refUpdateProcessor = { refUpdate ->
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