// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.RawProgressReporter
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenConsole
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper.LongRunningEmbedderTask
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.io.File
import java.nio.file.Path
import java.rmi.RemoteException
import java.util.*
import kotlin.collections.ArrayList

abstract class MavenEmbedderWrapper internal constructor(private val project: Project) :
  MavenRemoteObjectWrapper<MavenServerEmbedder?>(null) {

  @Synchronized
  @Throws(RemoteException::class)
  override fun getOrCreateWrappee(): MavenServerEmbedder {
    var embedder = super.getOrCreateWrappee()
    try {
      embedder.ping(ourToken)
    }
    catch (e: RemoteException) {
      onError()
      embedder = super.getOrCreateWrappee()
    }
    return embedder
  }

  private fun convertWorkspaceMap(map: MavenWorkspaceMap?): MavenWorkspaceMap? {
    if (null == map) return null
    val transformer = RemotePathTransformerFactory.createForProject(project)
    return if (transformer === RemotePathTransformerFactory.Transformer.ID) map
    else MavenWorkspaceMap.copy(map) {
      transformer.toRemotePath(it!!)
    }
  }

  @Throws(MavenProcessCanceledException::class)
  suspend fun resolveProject(files: Collection<VirtualFile>,
                             explicitProfiles: MavenExplicitProfiles,
                             progressReporter: RawProgressReporter,
                             syncConsole: MavenSyncConsole?,
                             console: MavenConsole?,
                             workspaceMap: MavenWorkspaceMap?,
                             updateSnapshots: Boolean): Collection<MavenServerExecutionResult> {
    val transformer = if (files.isEmpty()) RemotePathTransformerFactory.Transformer.ID
    else RemotePathTransformerFactory.createForProject(project)
    val ioFiles = files.map { file: VirtualFile -> transformer.toRemotePath(file.getPath())?.let { File(it) } }
    val serverWorkspaceMap = convertWorkspaceMap(workspaceMap)
    val request = ProjectResolutionRequest(
      ioFiles,
      explicitProfiles.enabledProfiles,
      explicitProfiles.disabledProfiles,
      serverWorkspaceMap,
      updateSnapshots
    )
    val results = runLongRunningTask(
      LongRunningEmbedderTask { embedder, taskId -> embedder.resolveProjects(taskId, request, ourToken) },
      progressReporter, syncConsole, console)
    if (transformer !== RemotePathTransformerFactory.Transformer.ID) {
      for (result in results) {
        val data = result.projectData ?: continue
        MavenBuildPathsChange({ transformer.toIdePath(it)!! }, { transformer.canBeRemotePath(it) }).perform(data.mavenModel)
      }
    }
    return results
  }

  @Throws(MavenProcessCanceledException::class)
  fun evaluateEffectivePom(file: VirtualFile, activeProfiles: Collection<String>, inactiveProfiles: Collection<String>): String? {
    return evaluateEffectivePom(File(file.getPath()), ArrayList(activeProfiles), ArrayList(inactiveProfiles))
  }

  @Throws(MavenProcessCanceledException::class)
  fun evaluateEffectivePom(file: File, activeProfiles: Collection<String>, inactiveProfiles: Collection<String>): String? {
    return performCancelable<String, RuntimeException> {
      getOrCreateWrappee().evaluateEffectivePom(file, ArrayList(activeProfiles), ArrayList(inactiveProfiles), ourToken)
    }
  }

  @Deprecated("use {@link MavenEmbedderWrapper#resolveArtifacts()}")
  @Throws(MavenProcessCanceledException::class)
  fun resolve(info: MavenArtifactInfo, remoteRepositories: List<MavenRemoteRepository>): MavenArtifact {
    val requests = listOf(MavenArtifactResolutionRequest(info, ArrayList(remoteRepositories)))
    return resolveArtifacts(requests, null, null, null)[0]
  }

  @Throws(MavenProcessCanceledException::class)
  fun resolveArtifacts(requests: Collection<MavenArtifactResolutionRequest>,
                       indicator: ProgressIndicator?,
                       syncConsole: MavenSyncConsole?,
                       console: MavenConsole?): List<MavenArtifact> {
    return runLongRunningTaskBlocking(
      LongRunningEmbedderTask { embedder, taskId -> embedder.resolveArtifacts(taskId, ArrayList(requests), ourToken) },
      indicator, syncConsole, console)
  }

  @Deprecated("use {@link MavenEmbedderWrapper#resolveArtifactTransitively()}")
  @Throws(MavenProcessCanceledException::class)
  fun resolveTransitively(artifacts: List<MavenArtifactInfo>, remoteRepositories: List<MavenRemoteRepository>): List<MavenArtifact> {
    return performCancelable<MavenArtifactResolveResult, RuntimeException> {
      getOrCreateWrappee().resolveArtifactsTransitively(ArrayList(artifacts), ArrayList(remoteRepositories), ourToken)
    }.mavenResolvedArtifacts
  }

  @Throws(MavenProcessCanceledException::class)
  fun resolveArtifactTransitively(artifacts: List<MavenArtifactInfo>,
                                  remoteRepositories: List<MavenRemoteRepository>): MavenArtifactResolveResult {
    return performCancelable<MavenArtifactResolveResult, RuntimeException> {
      getOrCreateWrappee().resolveArtifactsTransitively(ArrayList(artifacts), ArrayList(remoteRepositories), ourToken)
    }
  }

  @Throws(MavenProcessCanceledException::class)
  suspend fun resolvePlugins(mavenPluginRequests: Collection<Pair<MavenId, NativeMavenProjectHolder>>,
                             progressReporter: RawProgressReporter?,
                             syncConsole: MavenSyncConsole?,
                             console: MavenConsole?): List<PluginResolutionResponse> {
    val pluginResolutionRequests = ArrayList<PluginResolutionRequest>()
    for (mavenPluginRequest in mavenPluginRequests) {
      val mavenPluginId = mavenPluginRequest.first
      try {
        val id = mavenPluginRequest.second.getId()
        pluginResolutionRequests.add(PluginResolutionRequest(mavenPluginId, id))
      }
      catch (e: RemoteException) {
        // do not call handleRemoteError here since this error occurred because of previous remote error
        MavenLog.LOG.warn("Cannot resolve plugin: $mavenPluginId")
      }
    }
    return runLongRunningTask(
      LongRunningEmbedderTask { embedder, taskId -> embedder.resolvePlugins(taskId, pluginResolutionRequests, ourToken) },
      progressReporter, syncConsole, console)
  }

  @Throws(MavenProcessCanceledException::class)
  fun resolvePlugin(plugin: MavenPlugin, nativeMavenProject: NativeMavenProjectHolder): Collection<MavenArtifact> {
    val mavenId = plugin.mavenId
    return runBlockingMaybeCancellable {
      resolvePlugins(listOf(Pair.create(mavenId, nativeMavenProject)), null, null, null)
      .flatMap { resolutionResult: PluginResolutionResponse -> resolutionResult.artifacts }.toSet()
    }
  }

  @Throws(MavenProcessCanceledException::class)
  fun readModel(file: File?): MavenModel {
    return performCancelable<MavenModel, RuntimeException> { getOrCreateWrappee().readModel(file, ourToken) }
  }

  @Throws(MavenProcessCanceledException::class)
  fun executeGoal(requests: Collection<MavenGoalExecutionRequest?>,
                  goal: String,
                  progressIndicator: MavenProgressIndicator?,
                  console: MavenConsole?): List<MavenGoalExecutionResult> {
    val indicator = progressIndicator?.indicator
    val syncConsole = progressIndicator?.syncConsole
    return runLongRunningTaskBlocking(
      LongRunningEmbedderTask { embedder, taskId -> embedder.executeGoal(taskId, ArrayList(requests), goal, ourToken) },
      indicator, syncConsole, console)
  }

  fun resolveRepositories(repositories: Collection<MavenRemoteRepository?>): Set<MavenRemoteRepository> {
    return perform<Set<MavenRemoteRepository>, RuntimeException> { getOrCreateWrappee().resolveRepositories(ArrayList(repositories), ourToken) }
  }

  fun getInnerArchetypes(catalogPath: Path): Collection<MavenArchetype> {
    return perform<Collection<MavenArchetype>, RuntimeException> {
      getOrCreateWrappee().getLocalArchetypes(ourToken, catalogPath.toString())
    }
  }

  fun getRemoteArchetypes(url: String): Collection<MavenArchetype> {
    return perform<Collection<MavenArchetype>, RuntimeException> { getOrCreateWrappee().getRemoteArchetypes(ourToken, url) }
  }

  fun resolveAndGetArchetypeDescriptor(groupId: String,
                                       artifactId: String,
                                       version: String,
                                       repositories: List<MavenRemoteRepository>,
                                       url: String?): Map<String, String>? {
    return perform<Map<String, String>, RuntimeException> {
      getOrCreateWrappee().resolveAndGetArchetypeDescriptor(groupId, artifactId, version, ArrayList(repositories), url, ourToken)
    }
  }

  @Throws(RemoteException::class)
  @TestOnly
  fun getEmbedder(): MavenServerEmbedder = getOrCreateWrappee()

  fun release() {
    val w = wrappee ?: return
    try {
      w.release(ourToken)
    }
    catch (e: RemoteException) {
      handleRemoteError(e)
    }
  }

  // used in https://plugins.jetbrains.com/plugin/8053-azure-toolkit-for-intellij
  @Deprecated("This method does nothing (kept for a while for compatibility reasons).")
  fun clearCachesFor(projectId: MavenId?) {
  }

  @ApiStatus.Obsolete
  @Throws(MavenProcessCanceledException::class)
  private fun <R> runLongRunningTaskBlocking(task: LongRunningEmbedderTask<R>,
                                             indicator: ProgressIndicator?,
                                             syncConsole: MavenSyncConsole?,
                                             console: MavenConsole?): R {
    val longRunningTaskId = UUID.randomUUID().toString()
    val embedder = getOrCreateWrappee()

    @Suppress("RAW_RUN_BLOCKING")
    return runBlocking {
      val progressIndication = launch {
        while (isActive) {
          delay(500)
          blockingContext {
            val status = embedder.getLongRunningTaskStatus(longRunningTaskId, ourToken)
            indicator?.fraction = status.fraction()
            console?.handleConsoleEvents(status.consoleEvents())
            syncConsole?.handleDownloadEvents(status.downloadEvents())
            if (null != indicator && indicator.isCanceled) {
              if (embedder.cancelLongRunningTask(longRunningTaskId, ourToken)) {
                throw CancellationException()
              }
            }
          }
        }
      }

      try {
        withContext(Dispatchers.IO) {
          blockingContext {
            task.run(embedder, longRunningTaskId)
          }
        }
      }
      catch (e: Exception) {
        throw MavenProcessCanceledException(e)
      }
      finally {
        progressIndication.cancelAndJoin()
      }
    }
  }

  @Throws(MavenProcessCanceledException::class)
  private suspend fun <R> runLongRunningTask(task: LongRunningEmbedderTask<R>,
                                             progressReporter: RawProgressReporter?,
                                             syncConsole: MavenSyncConsole?,
                                             console: MavenConsole?): R {
    val longRunningTaskId = UUID.randomUUID().toString()
    val embedder = getOrCreateWrappee()

    return coroutineScope {
      val progressIndication = launch {
        while (isActive) {
          delay(500)
          blockingContext {
            val status = embedder.getLongRunningTaskStatus(longRunningTaskId, ourToken)
            progressReporter?.fraction(status.fraction())
            console?.handleConsoleEvents(status.consoleEvents())
            syncConsole?.handleDownloadEvents(status.downloadEvents())
          }
        }
      }

      progressIndication.invokeOnCompletion { cause ->
        if (cause is CancellationException) {
          try {
            embedder.cancelLongRunningTask(longRunningTaskId, ourToken)
          }
          catch (e: Exception) {
            MavenLog.LOG.warn("Exception in long running task cancellation", e)
          }
        }
      }

      try {
        withContext(Dispatchers.IO) {
          blockingContext {
            task.run(embedder, longRunningTaskId)
          }
        }
      }
      catch (e: Exception) {
        throw MavenProcessCanceledException(e)
      }
      finally {
        progressIndication.cancelAndJoin()
      }
    }
  }

  protected fun interface LongRunningEmbedderTask<R> {
    @Throws(RemoteException::class, MavenServerProcessCanceledException::class)
    fun run(embedder: MavenServerEmbedder, longRunningTaskId: String): R
  }
}
