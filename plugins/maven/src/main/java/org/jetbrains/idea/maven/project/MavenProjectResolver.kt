// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.util.ExceptionUtil
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.buildtool.MavenLogEventHandler
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.RepositoryBlockedSyncIssue.getIssue
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.MavenConfigBuildIssue.getIssue
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenProjectResolutionContributor.Companion.EP_NAME
import org.jetbrains.idea.maven.project.MavenResolveResultProblemProcessor.BLOCKED_MIRROR_FOR_REPOSITORIES
import org.jetbrains.idea.maven.project.MavenResolveResultProblemProcessor.MavenResolveProblemHolder
import org.jetbrains.idea.maven.server.*
import org.jetbrains.idea.maven.telemetry.tracer
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

@ApiStatus.Internal
data class MavenProjectResolutionResult(val mavenProjectMap: Map<String, Collection<MavenProject>>)

@ApiStatus.Internal
class MavenProjectResolverResult(
  @JvmField val file: File?,
  @JvmField val mavenModel: MavenModel,
  @JvmField val managedDependencies: List<MavenId>,
  @JvmField val dependencyHash: String?,
  @JvmField val dependencyResolutionSkipped: Boolean,
  @JvmField val nativeModelMap: Map<String, String>,
  @JvmField val activatedProfiles: MavenExplicitProfiles,
  @JvmField val readingProblems: MutableCollection<MavenProjectProblem>,
  @JvmField val unresolvedArtifactIds: MutableSet<MavenId>,
  val unresolvedProblems: Collection<MavenProjectProblem>,
)

@ApiStatus.Internal
interface MavenProjectResolutionContributor {
  @Deprecated("Use {@link #onMavenProjectResolved(Project, MavenProject, MavenEmbedderWrapper)}")
  suspend fun onMavenProjectResolved(
    project: Project,
    mavenProject: MavenProject,
    nativeMavenProject: NativeMavenProjectHolder,
    embedder: MavenEmbedderWrapper,
  ) {
    throw UnsupportedOperationException("Please implement ${this::class.qualifiedName}#onMavenProjectResolved(Project, MavenProject, MavenEmbedderWrapper)")
  }

  @Suppress("DEPRECATION")
  suspend fun onMavenProjectResolved(
    project: Project,
    mavenProject: MavenProject,
    embedder: MavenEmbedderWrapper,
  ) = onMavenProjectResolved(project, mavenProject, NativeMavenProjectHolder.NULL, embedder)

  companion object {
    val EP_NAME = ExtensionPointName.create<MavenProjectResolutionContributor>("org.jetbrains.idea.maven.projectResolutionContributor")
  }
}

@ApiStatus.Internal
class MavenProjectResolver(private val myProject: Project) {
  suspend fun resolve(
    incrementally: Boolean,
    mavenProjects: Collection<MavenProject>,
    tree: MavenProjectsTree,
    workspaceMap: MavenWorkspaceMap,
    effectiveRepositoryPath: Path,
    updateSnapshots: Boolean,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    progressReporter: RawProgressReporter,
    eventHandler: MavenEventHandler,
  ): MavenProjectResolutionResult {
    val projectsWithUnresolvedPlugins = HashMap<String, Collection<MavenProject>>()
    val projectMultiMap = MavenUtil.groupByBasedir(mavenProjects, tree)
    for ((baseDir, mavenProjectsInBaseDir) in projectMultiMap.entrySet()) {
      val embedder = mavenEmbedderWrappers.getEmbedder(baseDir)
      try {
        val userProperties = Properties()
        for (mavenProject in mavenProjectsInBaseDir) {
          @Suppress("DEPRECATION")
          for (mavenImporter in org.jetbrains.idea.maven.importing.MavenImporter.getSuitableImporters(mavenProject)) {
            mavenImporter.customizeUserProperties(myProject, mavenProject, userProperties)
          }
        }
        val pomToDependencyHash = mavenProjectsInBaseDir.associate { it.file to if (incrementally) it.dependencyHash else null }
        val projectsWithUnresolvedPluginsChunk = tracer.spanBuilder("doResolveMavenProject")
          .useWithScope {
            doResolve(
              pomToDependencyHash,
              mavenProjectsInBaseDir,
              tree,
              effectiveRepositoryPath,
              embedder,
              progressReporter,
              eventHandler,
              workspaceMap,
              updateSnapshots,
              userProperties)
          }
        projectsWithUnresolvedPlugins[baseDir] = projectsWithUnresolvedPluginsChunk
      }
      catch (t: Throwable) {
        processResolverException(t, true)
      }
    }
    MavenUtil.restartConfigHighlighting(mavenProjects)

    val pomToDependencyHash = tree.projects.associate { it.file to if (incrementally) it.dependencyHash else null }
    if (incrementally && updateSnapshots) {
      updateSnapshotsAfterIncrementalSync(tree, pomToDependencyHash, mavenEmbedderWrappers, progressReporter, eventHandler)
    }
    return MavenProjectResolutionResult(projectsWithUnresolvedPlugins)
  }

  private fun processResolverException(t: Throwable, rethrow: Boolean) {
    val cause = findParseException(t)
    if (cause != null) {
      val buildIssue = getIssue(cause)
      if (buildIssue != null) {
        MavenProjectsManager.getInstance(myProject).getSyncConsole().addBuildIssue(buildIssue, MessageEvent.Kind.ERROR)
      }
      else {
        if (rethrow) throw t
      }
    }
    else {
      MavenLog.LOG.warn("Error in maven config parsing", t)
      if (rethrow) throw t
    }
  }

  private suspend fun doResolve(
    pomToDependencyHash: Map<VirtualFile, String?>,
    mavenProjects: Collection<MavenProject>,
    tree: MavenProjectsTree,
    effectiveRepositoryPath: Path,
    embedder: MavenEmbedderWrapper,
    progressReporter: RawProgressReporter,
    eventHandler: MavenEventHandler,
    workspaceMap: MavenWorkspaceMap?,
    updateSnapshots: Boolean,
    userProperties: Properties,
  ): Collection<MavenProject> {
    if (mavenProjects.isEmpty()) return listOf()
    checkCanceled()
    MavenLog.LOG.debug("Project resolution started: ${mavenProjects.size}")
    val names = mavenProjects.map { it.displayName }
    val text = StringUtil.shortenPathWithEllipsis(StringUtil.join(names, ", "), 200)
    progressReporter.text(MavenProjectBundle.message("maven.resolving.pom", text))
    val explicitProfiles = tree.explicitProfiles
    val projects = tree.projects
    val pomDependencies = projects
      .associate { it.file to it.dependencies.filter { it.file.path.endsWith(MavenConstants.POM_XML) }.map { it.file }.toSet() }
      .filterValues { it.isNotEmpty() }
    val resultsAndProblems = resolveProjectsInEmbedder(
      embedder,
      pomToDependencyHash,
      pomDependencies,
      explicitProfiles,
      progressReporter,
      eventHandler,
      workspaceMap,
      updateSnapshots,
      userProperties)

    val results = resultsAndProblems.first
    val readingProblems = resultsAndProblems.second

    if (readingProblems.isNotEmpty()) {
      val pathToMavenProject = mavenProjects.associateBy { it.file.path }
      val groupedProblems = readingProblems.groupBy { FileUtil.toSystemIndependentName(trimLineAndColumn(it.path)) }
      for ((path, projectProblems) in groupedProblems) {
        val mavenProject = pathToMavenProject[path]
        if (null != mavenProject) {
          mavenProject.updateState(projectProblems)
          tree.fireProjectResolved(Pair.create(mavenProject, MavenProjectChanges.ALL))
        }
      }
    }

    val problems = getProblems(results, readingProblems)
    val problemsExist = !problems.isEmpty
    if (problemsExist) {
      MavenLog.LOG.debug(
        "Project resolution problems: ${problems.unresolvedArtifacts.size} ${problems.unresolvedArtifactProblems.size} ${problems.repositoryBlockedProblems.size}")
    }
    notifySyncForProblem(problems)
    val fileToMavenProject = mavenProjects.associateBy { it.file }
    val projectsWithUnresolvedPlugins = ConcurrentLinkedQueue<MavenProject>()

    coroutineScope {
      results.forEach {
        launch {
          collectProjectWithUnresolvedPlugins(it, fileToMavenProject, effectiveRepositoryPath, embedder, tree, projectsWithUnresolvedPlugins)
        }
      }
    }
    tree.recalculateMavenIdToProjectMap()
    MavenLog.LOG.debug("Project resolution finished: ${projectsWithUnresolvedPlugins.size}")
    return projectsWithUnresolvedPlugins
  }

  private suspend fun updateSnapshotsAfterIncrementalSync(
    tree: MavenProjectsTree,
    fileToDependencyHash: Map<VirtualFile, String?>,
    mavenEmbedderWrappers: MavenEmbedderWrappers,
    progressReporter: RawProgressReporter,
    eventHandler: MavenEventHandler,
  ) {
    val projectMultiMap = MavenUtil.groupByBasedir(tree.projects, tree)
    for ((baseDir, mavenProjectsForBaseDir) in projectMultiMap.entrySet()) {
      val embedder = mavenEmbedderWrappers.getEmbedder(baseDir)
      updateSnapshotsAfterIncrementalSync(mavenProjectsForBaseDir, fileToDependencyHash, embedder, progressReporter, eventHandler)
    }
  }

  private suspend fun updateSnapshotsAfterIncrementalSync(
    mavenProjects: Collection<MavenProject>,
    fileToDependencyHash: Map<VirtualFile, String?>,
    embedder: MavenEmbedderWrapper,
    progressReporter: RawProgressReporter,
    eventHandler: MavenEventHandler,
  ) {
    val requests = mutableSetOf<MavenArtifactResolutionRequest>()
    for (mavenProject in mavenProjects) {
      if (mavenProject.dependencyHash == fileToDependencyHash[mavenProject.file]) {
        // dependencies haven't changed and were not updated, so we need to force update SNAPSHOT dependencies
        for (dependency in mavenProject.dependencies) {
          if (dependency.baseVersion.endsWith("-SNAPSHOT")) {
            val artifactInfo = MavenArtifactInfo(
              dependency.groupId,
              dependency.artifactId,
              dependency.baseVersion,
              dependency.packaging,
              dependency.classifier)
            val request = MavenArtifactResolutionRequest(artifactInfo, mavenProject.remoteRepositories, true)
            requests.add(request)
          }
        }
      }
    }
    if (requests.isEmpty()) return
    embedder.resolveArtifacts(requests, progressReporter, eventHandler)
  }

  // trims line and column from file path, e.g., project/pom.xml:12:345 -> project/pom.xml
  private fun trimLineAndColumn(input: String): String {
    val regex = Regex(":[0-9]+:[0-9]+$")
    return input.replace(regex, "")
  }

  private fun getProblems(results: Collection<MavenProjectResolverResult>, problems: Collection<MavenProjectProblem>): MavenResolveProblemHolder {
    val repositoryBlockedProblems: MutableSet<MavenProjectProblem> = HashSet()
    val unresolvedArtifactProblems: MutableSet<MavenProjectProblem> = HashSet()
    val unresolvedArtifacts: MutableSet<MavenArtifact?> = HashSet()

    var hasProblem = false

    val readingProblems = problems + results.flatMap { it.readingProblems }

    for (problem in readingProblems) {
      if (!hasProblem) hasProblem = true
      if (problem.mavenArtifact != null) {
        if (unresolvedArtifacts.add(problem.mavenArtifact)) {
          unresolvedArtifactProblems.add(problem)
        }
      }
      val message = problem.description
      if (message != null && message.contains(BLOCKED_MIRROR_FOR_REPOSITORIES)) {
        repositoryBlockedProblems.add(problem)
      }
    }

    for (result in results) {
      for (problem in result.unresolvedProblems) {
        if (unresolvedArtifacts.add(problem.mavenArtifact)) {
          unresolvedArtifactProblems.add(problem)
        }
      }
    }
    return MavenResolveProblemHolder(repositoryBlockedProblems, unresolvedArtifactProblems, unresolvedArtifacts)
  }

  private fun notifySyncForProblem(problem: MavenResolveProblemHolder) {
    if (problem.isEmpty) return

    val syncConsole = MavenProjectsManager.getInstance(myProject).syncConsole
    for (projectProblem in problem.repositoryBlockedProblems) {
      if (projectProblem.description == null) continue
      val buildIssue = getIssue(myProject, projectProblem.description!!)
      syncConsole.showBuildIssue(buildIssue)
    }

    for (projectProblem in problem.unresolvedArtifactProblems) {
      if (projectProblem.mavenArtifact == null || projectProblem.description == null) continue
      syncConsole.showArtifactBuildIssue(MavenServerConsoleIndicator.ResolveType.DEPENDENCY,
                                         projectProblem.mavenArtifact!!.mavenId.key,
                                         projectProblem.description)
    }
  }

  private suspend fun resolveProjectsInEmbedder(
    embedder: MavenEmbedderWrapper,
    pomToDependencyHash: Map<VirtualFile, String?>,
    pomDependencies: Map<VirtualFile, Set<File>>,
    explicitProfiles: MavenExplicitProfiles,
    progressReporter: RawProgressReporter,
    eventHandler: MavenEventHandler,
    workspaceMap: MavenWorkspaceMap?,
    updateSnapshots: Boolean,
    userProperties: Properties,
  ): Pair<Collection<MavenProjectResolverResult>, Collection<MavenProjectProblem>> {
    val files = pomToDependencyHash.keys
    val resolverResults: MutableCollection<MavenProjectResolverResult> = ArrayList()
    val readingProblems = mutableListOf<MavenProjectProblem>()
    try {
      val executionResults = tracer.spanBuilder("resolveProjectInEmbedder")
        .useWithScope {
          embedder.resolveProject(
            pomToDependencyHash,
            pomDependencies,
            explicitProfiles,
            progressReporter,
            eventHandler,
            workspaceMap,
            updateSnapshots,
            userProperties)
        }
      val filesMap = CollectionFactory.createFilePathMap<VirtualFile>()
      filesMap.putAll(files.associateBy { it.path })
      for (result in executionResults) {
        val projectData = result.projectData
        if (projectData == null) {
          val file = detectPomFile(filesMap, result)
          MavenLog.LOG.warn("Project resolution: projectData is null, file $file")
          readingProblems.addAll(result.problems)
        }
        else {
          resolverResults.add(MavenProjectResolverResult(
            result.file,
            projectData.mavenModel,
            projectData.managedDependencies,
            projectData.dependencyHash,
            projectData.dependencyResolutionSkipped,
            projectData.mavenModelMap,
            MavenExplicitProfiles(projectData.activatedProfiles, explicitProfiles.disabledProfiles),
            result.problems,
            result.unresolvedArtifacts,
            result.unresolvedProblems))
        }
      }
    }
    catch (e: Throwable) {
      processResolverException(e, true)
    }
    return Pair.create(resolverResults, readingProblems)
  }

  private fun detectPomFile(filesMap: Map<String, VirtualFile>, result: MavenServerExecutionResult): VirtualFile? {
    if (filesMap.size == 1) {
      return filesMap.values.firstOrNull()
    }
    if (!result.problems.isEmpty()) {
      val path = result.problems.firstOrNull()?.path
      if (path != null) {
        return filesMap[FileUtil.toSystemIndependentName(path)]
      }
    }
    return null
  }

  private suspend fun collectProjectWithUnresolvedPlugins(
    result: MavenProjectResolverResult,
    fileToMavenProjects: Map<VirtualFile, MavenProject>,
    effectiveRepositoryPath: Path,
    embedder: MavenEmbedderWrapper,
    tree: MavenProjectsTree,
    projectsWithUnresolvedPlugins: ConcurrentLinkedQueue<MavenProject>,
  ) {
    val file = result.file
    if (file == null) {
      MavenLog.LOG.warn("Maven project file is null")
      return
    }
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(file.toPath())
    if (virtualFile == null) {
      MavenLog.LOG.warn("Maven project virtual file is null for $file")
      return
    }
    val mavenProject = fileToMavenProjects[virtualFile]
    if (mavenProject == null) {
      MavenLog.LOG.warn("Maven project not found for $file")
      return
    }
    val snapshot = mavenProject.snapshot
    val keepPreviousResolutionResults = MavenUtil.shouldKeepPreviousResolutionResults(result.readingProblems)
    val keepPreviousArtifacts = keepPreviousResolutionResults || result.dependencyResolutionSkipped

    MavenLog.LOG.debug(
      "Project resolution: updating maven project $mavenProject, keepPreviousArtifacts=$keepPreviousArtifacts, dependencies: ${result.mavenModel.dependencies.size}, managedDeps: ${result.managedDependencies.size}")

    MavenServerResultTransformer.getInstance(myProject)
      .transform(result.mavenModel, mavenProject.file)

    mavenProject.updateState(
      result.mavenModel,
      result.managedDependencies,
      result.dependencyHash,
      result.readingProblems,
      result.activatedProfiles,
      result.unresolvedArtifactIds,
      result.nativeModelMap,
      effectiveRepositoryPath,
      keepPreviousArtifacts,
      keepPreviousResolutionResults)

    for (contributor in EP_NAME.extensionList) {
      contributor.onMavenProjectResolved(myProject, mavenProject, embedder)
    }
    // project may be modified by MavenImporters, so we need to collect the changes after them:
    val changes = mavenProject.getChangesSinceSnapshot(snapshot)
    mavenProject.problems // need for fill problem cache
    tree.fireProjectResolved(Pair.create(mavenProject, changes))
    if (!mavenProject.hasReadingErrors()) {
      projectsWithUnresolvedPlugins.add(mavenProject)
    }
  }

  private fun findParseException(t: Throwable): MavenConfigParseException? {
    val parseException = ExceptionUtil.findCause(t, MavenConfigParseException::class.java)
    if (parseException != null) {
      return parseException
    }
    val cause = ExceptionUtil.getRootCause(t)
    if (cause is InvocationTargetException) {
      val target = cause.targetException
      if (target != null) {
        return ExceptionUtil.findCause(target, MavenConfigParseException::class.java)
      }
    }
    return null
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #resolveProject()}")
  internal fun resolveProjectSync(
    embedder: MavenEmbedderWrapper,
    files: Collection<VirtualFile>,
    explicitProfiles: MavenExplicitProfiles,
  ): Collection<MavenProjectResolverResult> {
    return runBlockingMaybeCancellable {
      resolveProjectsInEmbedder(
        embedder,
        files.associateWith { null },
        mapOf(),
        explicitProfiles,
        object : RawProgressReporter {},
        MavenLogEventHandler,
        null,
        false,
        Properties()).first
    }
  }
}
