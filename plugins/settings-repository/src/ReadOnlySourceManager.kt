/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.AtomicClearableLazyValue
import com.intellij.util.containers.mapSmartNotNull
import com.intellij.util.io.exists
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.jetbrains.settingsRepository.git.GitRepositoryClientImpl
import org.jetbrains.settingsRepository.git.Pull
import org.jetbrains.settingsRepository.git.upstream
import java.nio.file.Path

class ReadOnlySourceManager(private val settings: IcsSettings, val rootDir: Path) {
  private val repositoryList = object : AtomicClearableLazyValue<List<Repository>>() {
    override fun compute(): List<Repository> {
      if (settings.readOnlySources.isEmpty()) {
        return emptyList()
      }

      return settings.readOnlySources.mapSmartNotNull { source ->
        try {
          val path = source.path ?: return@mapSmartNotNull null
          val dir = rootDir.resolve(path)
          if (dir.exists()) {
            return@mapSmartNotNull FileRepositoryBuilder().setBare().setGitDir(dir.toFile()).build()
          }
          else {
            LOG.warn("Skip read-only source ${source.url} because dir doesn't exist")
          }
        }
        catch (e: Exception) {
          LOG.error(e)
        }

        null
      }
    }
  }

  val repositories: List<Repository>
    get() = repositoryList.value

  fun setSources(sources: List<ReadonlySource>) {
    settings.readOnlySources = sources
    repositoryList.drop()
  }

  fun update(indicator: ProgressIndicator? = null): Boolean {
    var isChanged = false
    for (repo in repositories) {
      indicator?.checkCanceled()
      LOG.debug { "Pull changes from read-only repo ${repo.upstream}" }

      if (Pull(GitRepositoryClientImpl(repo, icsManager.credentialsStore), indicator).fetch() != null) {
        isChanged = true
      }
    }
    return isChanged
  }
}