// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.progress.checkCancelled
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.util.ExceptionUtil
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.RepositoryBlockedSyncIssue.getIssue
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.MavenConfigBuildIssue.getIssue
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenResolveResultProblemProcessor.BLOCKED_MIRROR_FOR_REPOSITORIES
import org.jetbrains.idea.maven.project.MavenResolveResultProblemProcessor.MavenResolveProblemHolder
import org.jetbrains.idea.maven.server.*
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.ParallelRunner
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

@ApiStatus.Internal
data class MavenProjectResolutionResult(val mavenProjectMap: Map<String, Collection<MavenProjectWithHolder>>)

@ApiStatus.Internal
class MavenProjectResolverResult(@JvmField val mavenModel: MavenModel,
                                 @JvmField val dependencyHash: String?,
                                 @JvmField val nativeModelMap: Map<String, String?>,
                                 @JvmField val activatedProfiles: MavenExplicitProfiles,
                                 val nativeMavenProject: NativeMavenProjectHolder?,
                                 @JvmField val readingProblems: MutableCollection<MavenProjectProblem>,
                                 @JvmField val unresolvedArtifactIds: MutableSet<MavenId>,
                                 val unresolvedProblems: Collection<MavenProjectProblem>) {
  constructor(readerResult: MavenProjectReaderResult) :
    this(
      readerResult.mavenModel,
      null,
      readerResult.nativeModelMap,
      readerResult.activatedProfiles,
      null,
      readerResult.readingProblems,
      mutableSetOf(),
      emptyList(),
    )

}

@ApiStatus.Internal
class MavenProjectResolver(private val myProject: Project) {
  suspend fun resolve(incrementally: Boolean,
                      mavenProjects: Collection<MavenProject>,
                      tree: MavenProjectsTree,
                      generalSettings: MavenGeneralSettings,
                      embeddersManager: MavenEmbeddersManager,
                      progressReporter: RawProgressReporter,
                      eventHandler: MavenEventHandler): MavenProjectResolutionResult {
    val updateSnapshots = MavenProjectsManager.getInstance(myProject).forceUpdateSnapshots || generalSettings.isAlwaysUpdateSnapshots
    val projectsWithUnresolvedPlugins = HashMap<String, Collection<MavenProjectWithHolder>>()
    val projectMultiMap = MavenUtil.groupByBasedir(mavenProjects, tree)
    for ((baseDir, mavenProjectsInBaseDir) in projectMultiMap.entrySet()) {
      val embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, baseDir)
      try {
        val userProperties = Properties()
        for (mavenProject in mavenProjectsInBaseDir) {
          mavenProject.configFileError = null
          for (mavenImporter in MavenImporter.getSuitableImporters(mavenProject)) {
            mavenImporter.customizeUserProperties(myProject, mavenProject, userProperties)
          }
        }
        val projectsWithUnresolvedPluginsChunk = doResolve(
          incrementally,
          mavenProjectsInBaseDir,
          tree,
          generalSettings,
          embedder,
          progressReporter,
          eventHandler,
          tree.workspaceMap,
          updateSnapshots,
          userProperties)
        projectsWithUnresolvedPlugins[baseDir] = projectsWithUnresolvedPluginsChunk
      }
      catch (t: Throwable) {
        val cause = findParseException(t)
        if (cause != null) {
          val buildIssue = getIssue(cause)
          if (buildIssue != null) {
            MavenProjectsManager.getInstance(myProject).getSyncConsole().addBuildIssue(buildIssue, MessageEvent.Kind.ERROR)
          }
          else {
            throw t
          }
        }
        else {
          MavenLog.LOG.warn("Error in maven config parsing", t)
          throw t
        }
      }
      finally {
        embeddersManager.release(embedder)
      }
    }
    MavenUtil.restartConfigHighlighting(mavenProjects)
    return MavenProjectResolutionResult(projectsWithUnresolvedPlugins)
  }

  private suspend fun doResolve(incrementally: Boolean,
                                mavenProjects: Collection<MavenProject>,
                                tree: MavenProjectsTree,
                                generalSettings: MavenGeneralSettings,
                                embedder: MavenEmbedderWrapper,
                                progressReporter: RawProgressReporter,
                                eventHandler: MavenEventHandler,
                                workspaceMap: MavenWorkspaceMap?,
                                updateSnapshots: Boolean,
                                userProperties: Properties): Collection<MavenProjectWithHolder> {
    if (mavenProjects.isEmpty()) return listOf()
    checkCancelled()
    MavenLog.LOG.debug("Project resolution started: ${mavenProjects.size}")
    val names = mavenProjects.map { it.displayName }
    val text = StringUtil.shortenPathWithEllipsis(StringUtil.join(names, ", "), 200)
    progressReporter.text(MavenProjectBundle.message("maven.resolving.pom", text))
    val explicitProfiles = tree.explicitProfiles
    val fileToDependencyHash = mavenProjects.associate { it.file to if (incrementally) it.dependencyHash else null }
    val results = resolveProject(
      MavenProjectReader(myProject),
      generalSettings,
      embedder,
      fileToDependencyHash,
      explicitProfiles,
      tree.projectLocator,
      progressReporter,
      eventHandler,
      workspaceMap,
      updateSnapshots,
      userProperties)
    val problems = getProblems(results)
    val problemsExist = !problems.isEmpty
    if (problemsExist) {
      MavenLog.LOG.debug(
        "Project resolution problems: ${problems.unresolvedArtifacts.size} ${problems.unresolvedArtifactProblems.size} ${problems.repositoryBlockedProblems.size}")
    }
    notifySyncForProblem(problems)
    val artifactIdToMavenProjects = mavenProjects
      .groupBy { mavenProject -> mavenProject.mavenId.artifactId }
      .filterKeys { it != null }
      .mapKeys { it.key!! }
    val projectsWithUnresolvedPlugins = ConcurrentLinkedQueue<MavenProjectWithHolder>()

    ParallelRunner.getInstance(myProject).runInParallel(results) {
      doResolve(it, artifactIdToMavenProjects, generalSettings, embedder, tree, projectsWithUnresolvedPlugins)
    }
    MavenLog.LOG.debug("Project resolution finished: ${projectsWithUnresolvedPlugins.size}")
    return projectsWithUnresolvedPlugins
  }

  fun getProblems(results: Collection<MavenProjectResolverResult>): MavenResolveProblemHolder {
    val repositoryBlockedProblems: MutableSet<MavenProjectProblem> = HashSet()
    val unresolvedArtifactProblems: MutableSet<MavenProjectProblem> = HashSet()
    val unresolvedArtifacts: MutableSet<MavenArtifact?> = HashSet()

    var hasProblem = false
    for (result in results) {
      for (problem in result.readingProblems) {
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

  private suspend fun resolveProject(reader: MavenProjectReader,
                                     generalSettings: MavenGeneralSettings,
                                     embedder: MavenEmbedderWrapper,
                                     fileToDependencyHash: Map<VirtualFile, String?>,
                                     explicitProfiles: MavenExplicitProfiles,
                                     locator: MavenProjectReaderProjectLocator,
                                     progressReporter: RawProgressReporter,
                                     eventHandler: MavenEventHandler,
                                     workspaceMap: MavenWorkspaceMap?,
                                     updateSnapshots: Boolean,
                                     userProperties: Properties): Collection<MavenProjectResolverResult> {
    val files = fileToDependencyHash.keys
    return try {
      val executionResults = embedder.resolveProject(
        fileToDependencyHash, explicitProfiles, progressReporter, eventHandler, workspaceMap, updateSnapshots, userProperties)
      val filesMap = CollectionFactory.createFilePathMap<VirtualFile>()
      filesMap.putAll(files.associateBy { it.path })
      val resolverResults: MutableCollection<MavenProjectResolverResult> = ArrayList()
      for (result in executionResults) {
        val projectData = result.projectData
        if (projectData == null) {
          val file = detectPomFile(filesMap, result)
          MavenLog.LOG.debug("Project resolution: projectData is null, file $file")
          if (file != null) {
            val readerResult = reader.readProjectAsync(generalSettings, file, explicitProfiles, locator)
            val temp = MavenProjectResolverResult(readerResult)
            temp.readingProblems.addAll(result.problems)
            temp.unresolvedArtifactIds.addAll(result.unresolvedArtifacts)
            resolverResults.add(temp)
            MavenLog.LOG.debug("Project resolution: projectData is null, read project")
          }
        }
        else {
          resolverResults.add(MavenProjectResolverResult(
            projectData.mavenModel,
            projectData.dependencyHash,
            projectData.mavenModelMap,
            MavenExplicitProfiles(projectData.activatedProfiles, explicitProfiles.disabledProfiles),
            projectData.nativeMavenProject,
            result.problems,
            result.unresolvedArtifacts,
            result.unresolvedProblems))
        }
      }
      resolverResults
    }
    catch (e: Throwable) {
      MavenLog.LOG.info(e)
      MavenLog.printInTests(e) // print exception since we need to know if something wrong with our logic
      files.map {
        val result = reader.readProjectAsync(generalSettings, it, explicitProfiles, locator)
        val message = e.message
        if (message != null) {
          result.readingProblems.add(MavenProjectProblem.createStructureProblem(it.getPath(), message))
        }
        else {
          result.readingProblems.add(MavenProjectProblem.createSyntaxProblem(it.getPath(), MavenProjectProblem.ProblemType.SYNTAX))
        }
        MavenProjectResolverResult(result)
      }
    }
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

  private fun doResolve(result: MavenProjectResolverResult,
                        artifactIdToMavenProjects: Map<String, List<MavenProject>>,
                        generalSettings: MavenGeneralSettings,
                        embedder: MavenEmbedderWrapper,
                        tree: MavenProjectsTree,
                        projectsWithUnresolvedPlugins: ConcurrentLinkedQueue<MavenProjectWithHolder>) {
    val mavenId = result.mavenModel.mavenId
    val artifactId = mavenId.artifactId
    val mavenProjects = artifactIdToMavenProjects[artifactId]
    if (mavenProjects == null) {
      MavenLog.LOG.warn("Maven projects not found for $artifactId")
      return
    }
    var mavenProjectCandidate: MavenProject? = null
    for (mavenProject in mavenProjects) {
      if (mavenProject.mavenId == mavenId) {
        mavenProjectCandidate = mavenProject
        break
      }
      else if (mavenProject.mavenId.equals(mavenId.groupId, mavenId.artifactId)) {
        mavenProjectCandidate = mavenProject
      }
    }
    if (mavenProjectCandidate == null) {
      MavenLog.LOG.warn("Maven project not found for $artifactId")
      return
    }
    val snapshot = mavenProjectCandidate.snapshot
    val keepPreviousArtifacts = MavenUtil.shouldKeepPreviousArtifacts(result.readingProblems)

    MavenLog.LOG.debug(
      "Project resolution: updating maven project $mavenProjectCandidate, keepPreviousArtifacts=$keepPreviousArtifacts, dependencies: ${result.mavenModel.dependencies.size}")

    mavenProjectCandidate.updateFromReaderResult(
      result.mavenModel,
      result.dependencyHash,
      result.readingProblems,
      result.activatedProfiles,
      result.unresolvedArtifactIds,
      result.nativeModelMap,
      generalSettings,
      false,
      keepPreviousArtifacts,
      true)

    val nativeMavenProject = result.nativeMavenProject
    if (nativeMavenProject != null) {
      for (eachImporter in MavenImporter.getSuitableImporters(mavenProjectCandidate)) {
        eachImporter.resolve(myProject, mavenProjectCandidate, nativeMavenProject, embedder)
      }
    }
    else {
      MavenLog.LOG.warn("Native maven project not found for $artifactId")
    }
    // project may be modified by MavenImporters, so we need to collect the changes after them:
    val changes = mavenProjectCandidate.getChangesSinceSnapshot(snapshot)
    mavenProjectCandidate.getProblems() // need for fill problem cache
    tree.fireProjectResolved(Pair.create(mavenProjectCandidate, changes), nativeMavenProject)
    if (!mavenProjectCandidate.hasReadingProblems()) {
      if (null != nativeMavenProject) {
        projectsWithUnresolvedPlugins.add(MavenProjectWithHolder(mavenProjectCandidate, nativeMavenProject, changes))
      }
      else {
        MavenLog.LOG.error("Native maven project is null for $mavenProjectCandidate")
      }
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


  @Deprecated("Use {@link #resolveProject()}")
  internal fun resolveProjectSync(reader: MavenProjectReader,
                                  generalSettings: MavenGeneralSettings,
                                  embedder: MavenEmbedderWrapper,
                                  files: Collection<VirtualFile>,
                                  explicitProfiles: MavenExplicitProfiles,
                                  locator: MavenProjectReaderProjectLocator,
                                  progressReporter: RawProgressReporter,
                                  eventHandler: MavenEventHandler,
                                  workspaceMap: MavenWorkspaceMap?,
                                  updateSnapshots: Boolean): Collection<MavenProjectResolverResult> {
    return runBlockingMaybeCancellable {
      resolveProject(
        reader,
        generalSettings,
        embedder,
        files.associateWith { null },
        explicitProfiles,
        locator,
        progressReporter,
        eventHandler,
        workspaceMap,
        updateSnapshots,
        Properties())
    }
  }
}
