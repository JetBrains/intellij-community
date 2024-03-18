package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import java.util.concurrent.CompletableFuture

/**
 * The command downloads maven artifacts (sources, docs)
 * Syntax: %downloadMavenArtifacts download_sources(boolean) download_docs(boolean)
 * Example: %downloadMavenArtifacts true true - download sources and docs
 * Example: %downloadMavenArtifacts true false - download sources
 * Example: %downloadMavenArtifacts false true - download docs
 */
class DownloadMavenArtifactsCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "downloadMavenArtifacts"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val (sources, docs) = extractCommandList(PREFIX, " ").map { it.toBoolean() }
    val manager = MavenProjectsManager.getInstance(project)
    val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
    cs.launch {
      manager.downloadArtifacts(manager.projects, null, sources, docs)
    }
    val promise = CompletableFuture<Any>()
    project.messageBus.connect()
      .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun artifactDownloadingFinished() {
          promise.complete(null)
        }
      })

    promise.join()
  }

  override fun getName(): String {
    return NAME
  }
}