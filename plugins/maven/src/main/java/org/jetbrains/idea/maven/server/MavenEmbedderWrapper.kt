// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext
import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.buildtool.MavenLogEventHandler
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.importing.output.MavenImportOutputParser
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenConsole
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.telemetry.tracer
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.io.File
import java.io.Serializable
import java.nio.file.Path
import java.rmi.RemoteException
import java.util.*
import java.util.concurrent.TimeUnit

// FIXME: still some missing transforms?
abstract class MavenEmbedderWrapper internal constructor(private val project: Project) :
  MavenRemoteObjectWrapper<MavenServerEmbedder?>() {

  @Throws(RemoteException::class)
  override suspend fun getOrCreateWrappee(): MavenServerEmbedder {
    var embedder = super.getOrCreateWrappee()
    try {
      embedder!!.ping(ourToken)
    }
    catch (e: RemoteException) {
      onError()
      embedder = super.getOrCreateWrappee()
    }
    return embedder!!
  }

  private fun convertWorkspaceMap(map: MavenWorkspaceMap?): MavenWorkspaceMap? {
    if (null == map) return null
    val transformer = RemotePathTransformerFactory.createForProject(project)
    return if (transformer === RemotePathTransformerFactory.Transformer.ID) map
    else MavenWorkspaceMap.copy(map) {
      transformer.toRemotePath(it!!)
    }
  }

  suspend fun resolveProject(
    filesToResolve: List<VirtualFile>,
    pomToDependencyHash: Map<VirtualFile, String?>,
    pomDependencies: Map<VirtualFile, Set<File>>,
    explicitProfiles: MavenExplicitProfiles,
    progressReporter: RawProgressReporter,
    eventHandler: MavenEventHandler,
    workspaceMap: MavenWorkspaceMap?,
    updateSnapshots: Boolean,
    userProperties: Properties,
  ): Collection<MavenServerExecutionResult> {
    val transformer = RemotePathTransformerFactory.createForProject(project)

    val pomHashMap = PomHashMap()
    pomToDependencyHash.mapNotNull { (file, checkSum) ->
      transformer.toRemotePath(file.getPath())?.let {
        pomHashMap.put(File(it), checkSum)
      }
    }

    pomDependencies.map { (file, dependencies) ->
      transformer.toRemotePath(file.getPath())?.let {
        pomHashMap.addFileDependencies(File(it), dependencies.mapNotNull { transformer.toRemotePath(it.path) }.map { File(it) })
      }
    }

    val serverWorkspaceMap = convertWorkspaceMap(workspaceMap)

    val toResolve = filesToResolve.mapNotNull { file -> transformer.toRemotePath(file.getPath())?.let { File(it) } }
    val request = ProjectResolutionRequest(
      toResolve,
      pomHashMap,
      explicitProfiles.enabledProfiles,
      explicitProfiles.disabledProfiles,
      serverWorkspaceMap,
      updateSnapshots,
      userProperties
    )

    val results = runLongRunningTask(
      LongRunningEmbedderTask { embedder, taskInput -> embedder.resolveProjects(taskInput, request, ourToken) },
      progressReporter, eventHandler)

    for (result in results) {
      val data = result.projectData ?: continue
      MavenServerResultTransformer.transformPaths(transformer, data.mavenModel)
    }
    if (transformer == RemotePathTransformerFactory.Transformer.ID) return results
    return results.map {
      MavenServerExecutionResult(
        it.file.toIdePath(transformer),
        it.projectData,
        it.problems,
        it.unresolvedArtifacts,
        it.unresolvedProblems)
    }
  }

  private fun File?.toIdePath(transformer: RemotePathTransformerFactory.Transformer): File? {
    if (this == null) return null
    return File(transformer.toIdePath(this.path) ?: this.path)
  }

  fun evaluateEffectivePom(file: VirtualFile, activeProfiles: Collection<String>, inactiveProfiles: Collection<String>): String? {
    return runBlockingMaybeCancellable {
      evaluateEffectivePom(File(file.getPath()), ArrayList(activeProfiles), ArrayList(inactiveProfiles))
    }
  }

  suspend fun evaluateEffectivePom(file: File, activeProfiles: Collection<String>, inactiveProfiles: Collection<String>): String? {
    return runLongRunningTask(
      LongRunningEmbedderTask { embedder, taskInput ->
        embedder.evaluateEffectivePom(taskInput, file, ArrayList(activeProfiles), ArrayList(inactiveProfiles), ourToken)
      }, null, MavenLogEventHandler)
  }

  @Deprecated("use {@link MavenEmbedderWrapper#resolveArtifacts()}")
  fun resolve(info: MavenArtifactInfo, remoteRepositories: List<MavenRemoteRepository>): MavenArtifact {
    val requests = listOf(MavenArtifactResolutionRequest(info, ArrayList(remoteRepositories)))
    return runBlockingMaybeCancellable { resolveArtifacts(requests, null, MavenLogEventHandler)[0] }
  }

  @Deprecated("use {@link MavenEmbedderWrapper#resolveArtifacts(requests, indicator, syncConsole)}",
              ReplaceWith("resolveArtifacts(requests, indicator, syncConsole)"))
  fun resolveArtifacts(
    requests: Collection<MavenArtifactResolutionRequest>,
    indicator: ProgressIndicator?,
    syncConsole: MavenSyncConsole?,
    console: MavenConsole?,
  ): List<MavenArtifact> {
    return runBlockingMaybeCancellable { resolveArtifacts(requests, null, syncConsole ?: MavenLogEventHandler) }
  }

  private fun MavenArtifact.transform(): MavenArtifact {
    val transformer = RemotePathTransformerFactory.createForProject(project)

    val idePath = transformer.toIdePath(file.path)

    return if (idePath != null) {
      replaceFile(File(idePath), null)
    }
    else {
      this
    }
  }

  private fun List<MavenArtifact>.transform(): List<MavenArtifact> {
    return map { it.transform() }
  }

  suspend fun resolveArtifacts(
    requests: Collection<MavenArtifactResolutionRequest>,
    progressReporter: RawProgressReporter?,
    eventHandler: MavenEventHandler,
  ): List<MavenArtifact> {
    return runLongRunningTask(
      LongRunningEmbedderTask { embedder, taskInput -> embedder.resolveArtifacts(taskInput, ArrayList(requests), ourToken) },
      progressReporter, eventHandler).transform()
  }

  private fun MavenArtifactResolveResult.transform(): MavenArtifactResolveResult {
    return MavenArtifactResolveResult(mavenResolvedArtifacts.transform(), problem)
  }

  @Deprecated("use {@link MavenEmbedderWrapper#resolveArtifactsTransitively()}")
  fun resolveArtifactTransitively(
    artifacts: List<MavenArtifactInfo>,
    remoteRepositories: List<MavenRemoteRepository>,
  ): MavenArtifactResolveResult {
    if (artifacts.isEmpty()) return MavenArtifactResolveResult(emptyList(), null)
    return runBlockingMaybeCancellable {
      runLongRunningTask(
        LongRunningEmbedderTask { embedder, taskInput ->
          embedder.resolveArtifactsTransitively(taskInput, ArrayList(artifacts), ArrayList(remoteRepositories), ourToken)
        }, null, MavenLogEventHandler)

    }.transform()
  }

  suspend fun resolveArtifactsTransitively(
    artifacts: List<MavenArtifactInfo>,
    remoteRepositories: List<MavenRemoteRepository>,
  ): MavenArtifactResolveResult {
    if (artifacts.isEmpty()) return MavenArtifactResolveResult(emptyList(), null)
    return runLongRunningTask(
      LongRunningEmbedderTask { embedder, taskInput ->
        embedder.resolveArtifactsTransitively(taskInput, ArrayList(artifacts), ArrayList(remoteRepositories), ourToken)
      }, null, MavenLogEventHandler)  }

  private fun MavenArtifact.transformPaths(transformer: RemotePathTransformerFactory.Transformer) = this.replaceFile(
    File(transformer.toIdePath(file.path)!!),
    null
  )

  private fun PluginResolutionResponse.transformPaths(transformer: RemotePathTransformerFactory.Transformer): PluginResolutionResponse {
    return PluginResolutionResponse(
      this.mavenPluginId,
      this.pluginArtifact?.transformPaths(transformer),
      this.pluginDependencyArtifacts.map { it.transformPaths(transformer) }
    )
  }

  suspend fun resolvePlugins(
    resolutionRequests: List<PluginResolutionRequest>,
    progressReporter: RawProgressReporter?,
    eventHandler: MavenEventHandler,
    forceUpdateSnapshots: Boolean,
  ): List<PluginResolutionResponse> {
    val responseList = runLongRunningTask(
      LongRunningEmbedderTask { embedder, taskInput ->
        embedder.resolvePlugins(taskInput, ArrayList(resolutionRequests), forceUpdateSnapshots, ourToken)
      },
      progressReporter, eventHandler)

    val transformer = RemotePathTransformerFactory.createForProject(project)
    if (transformer == RemotePathTransformerFactory.Transformer.ID) return responseList
    return responseList.map { it.transformPaths(transformer) }
  }

  fun resolvePlugin(
    plugin: MavenPlugin,
    mavenProject: MavenProject,
    forceUpdateSnapshots: Boolean,
  ): Collection<MavenArtifact> {
    val mavenId = plugin.mavenId
    val dependencies = plugin.dependencies.map { MavenId(it.groupId, it.artifactId, it.version) }
    val resolutionRequests = listOf(PluginResolutionRequest(mavenId, mavenProject.remotePluginRepositories, true, dependencies))
    return runBlockingMaybeCancellable {
      resolvePlugins(resolutionRequests, null, MavenLogEventHandler, forceUpdateSnapshots)
        .flatMap { resolutionResult: PluginResolutionResponse -> resolutionResult.pluginDependencyArtifacts.transform() }.toSet()
    }
  }

  suspend fun readModel(file: File?): MavenModel? {
    return getOrCreateWrappee().readModel(file, ourToken)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("use suspend method")
  fun executeGoal(
    requests: Collection<MavenGoalExecutionRequest>,
    goal: String,
    progressIndicator: MavenProgressIndicator?,
    console: MavenConsole,
  ): List<MavenGoalExecutionResult> {
    val progressReporter = object : RawProgressReporter {
      override fun text(text: @NlsContexts.ProgressText String?) {
        progressIndicator?.indicator?.text = text
      }
    }
    return runBlockingMaybeCancellable { executeGoal(requests, goal, progressReporter, console) }
  }

  suspend fun executeGoal(
    requests: Collection<MavenGoalExecutionRequest>,
    goal: String,
    progressReporter: RawProgressReporter,
    eventHandler: MavenEventHandler,
  ): List<MavenGoalExecutionResult> {
    return runLongRunningTask(
      LongRunningEmbedderTask { embedder, taskInput -> embedder.executeGoal(taskInput, ArrayList(requests), goal, ourToken) },
      progressReporter, eventHandler)
  }

  fun resolveRepositories(repositories: Collection<MavenRemoteRepository>): Set<MavenRemoteRepository> {
    return runBlockingMaybeCancellable {
      getOrCreateWrappee().resolveRepositories(ArrayList(repositories), ourToken)
    }
  }

  fun getInnerArchetypes(catalogPath: Path): Collection<MavenArchetype> {
    return runBlockingMaybeCancellable {
      getOrCreateWrappee().getLocalArchetypes(ourToken, catalogPath.toString())
    }
  }

  fun getRemoteArchetypes(url: String): Collection<MavenArchetype> {
    return runBlockingMaybeCancellable { getOrCreateWrappee().getRemoteArchetypes(ourToken, url) }
  }

  fun resolveAndGetArchetypeDescriptor(
    groupId: String,
    artifactId: String,
    version: String,
    repositories: List<MavenRemoteRepository>,
    url: String?,
  ): Map<String, String>? {
    return runBlockingMaybeCancellable {
      getOrCreateWrappee().resolveAndGetArchetypeDescriptor(groupId, artifactId, version, ArrayList(repositories), url, ourToken)
    }
  }

  @Throws(RemoteException::class)
  @TestOnly
  suspend fun getEmbedder(): MavenServerEmbedder = getOrCreateWrappee()

  fun release() {
    val w = myWrappee ?: return
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

  private suspend fun <R : Serializable> runLongRunningTask(
    task: LongRunningEmbedderTask<R>,
    progressReporter: RawProgressReporter?,
    eventHandler: MavenEventHandler,
  ): R {
    val longRunningTaskId = UUID.randomUUID().toString()
    val embedder = tracer.spanBuilder("getOrCreateWrappee").useWithScope {
      getOrCreateWrappee()
    }

    return coroutineScope {
      val progressIndication = launch {
        tracer.spanBuilder("waitForLongRunningTask").useWithScope { span ->
          while (isActive) {
            span.addEvent("poll", System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            delay(500)
            try {
              val status = embedder.getLongRunningTaskStatus(longRunningTaskId, ourToken)
              val fraction = status.fraction()
              if (fraction > 1.0) {
                MavenLog.LOG.warn("fraction is more than one: $status")
              }
              progressReporter?.fraction(fraction.coerceAtMost(1.0))
              processImportEvents(status)
              eventHandler.handleConsoleEvents(status.consoleEvents())
              eventHandler.handleDownloadEvents(status.downloadEvents())
              handleDownloadArtifactEvents(status)
            }
            catch (e: Throwable) {
              if (isActive) {
                throw e
              }
            }
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
          tracer.spanBuilder("runMavenExecution").useWithScope { span ->
            val longRunningTaskInput = LongRunningTaskInput(longRunningTaskId, TelemetryContext.current())
            val response = task.run(embedder, longRunningTaskInput)
            val status = response.status
            processImportEvents(status)
            eventHandler.handleConsoleEvents(status.consoleEvents())
            eventHandler.handleDownloadEvents(status.downloadEvents())
            handleDownloadArtifactEvents(status)
            response.result
          }
        }
      }
      finally {
        progressIndication.cancelAndJoin()
      }
    }
  }

  private fun handleDownloadArtifactEvents(status: LongRunningTaskStatus) {
    val artifactEvents = status.downloadArtifactEvents()
    for (e in artifactEvents) {
      ApplicationManager.getApplication().messageBus.syncPublisher(MavenServerConnector.DOWNLOAD_LISTENER_TOPIC).artifactDownloaded(
        File(e.file))
    }
  }

  private fun processImportEvents(
    status: LongRunningTaskStatus,
  ) {
    val console = MavenProjectsManager.getInstance(project).syncConsole
    val outputParser = MavenImportOutputParser(project)
    status.consoleEvents().forEach { consoleEvent ->
      val prefix =
        if (consoleEvent.level == MavenServerConsoleIndicator.LEVEL_ERROR || consoleEvent.level == MavenServerConsoleIndicator.LEVEL_FATAL) {
          "[ERROR]"
        }
        else if (consoleEvent.level == MavenServerConsoleIndicator.LEVEL_WARN) {
          "[WARNING]"
        }
        else if (consoleEvent.level == MavenServerConsoleIndicator.LEVEL_INFO) {
          "[INFO]"
        }
        else if (consoleEvent.level == MavenServerConsoleIndicator.LEVEL_DEBUG) {
          "[DEBUG]"
        }
        else ""

      val message = if (consoleEvent.message.startsWith('[')) consoleEvent.message else "$prefix ${consoleEvent.message}"

      outputParser.parse(message, null) {
        console.addBuildEvent(it)
      }
    }
  }

  protected fun interface LongRunningEmbedderTask<R : Serializable> {
    fun run(embedder: MavenServerEmbedder, longRunningTaskInput: LongRunningTaskInput): MavenServerResponse<R>
  }
}
