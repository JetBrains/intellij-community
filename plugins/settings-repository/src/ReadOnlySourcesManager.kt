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

import com.intellij.util.SmartList
import com.intellij.util.exists
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

class ReadOnlySourcesManager(private val settings: IcsSettings, val rootDir: Path) {
  private var _repositories: List<Repository>? = null

  val repositories: List<Repository>
    get() {
      var r = _repositories
      if (r == null) {
        if (settings.readOnlySources.isEmpty()) {
          r = emptyList()
        }
        else {
          r = SmartList<Repository>()
          for (source in settings.readOnlySources) {
            try {
              val path = source.path ?: continue
              val dir = rootDir.resolve(path)
              if (dir.exists()) {
                r.add(FileRepositoryBuilder().setBare().setGitDir(dir.toFile()).build())
              }
              else {
                LOG.warn("Skip read-only source ${source.url} because dir doesn't exists")
              }
            }
            catch (e: Exception) {
              LOG.error(e)
            }
          }
        }
        _repositories = r
      }
      return r
    }

  fun setSources(sources: List<ReadonlySource>) {
    settings.readOnlySources = sources
    _repositories = null
  }

  @TestOnly fun sourceToDir(source: ReadonlySource) = rootDir.resolve(source.path!!)
}