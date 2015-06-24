package org.jetbrains.settingsRepository

import com.intellij.util.SmartList
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class ReadOnlySourcesManager(private val settings: IcsSettings) {
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
              val dir = File(getPluginSystemDir(), path)
              if (dir.exists()) {
                r.add(FileRepositoryBuilder().setBare().setGitDir(dir).build())
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
}