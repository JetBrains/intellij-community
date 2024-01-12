// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.model.MavenWorkspaceMap
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenServerExecutionResult
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import java.util.*

internal class MavenProjectResolutionUtil {
  companion object {
    @JvmStatic
    @Throws(MavenProcessCanceledException::class)
    @Deprecated("Use {@link #resolveProject()}")
    fun resolveProjectSync(reader: MavenProjectReader,
                           generalSettings: MavenGeneralSettings,
                           embedder: MavenEmbedderWrapper,
                           files: Collection<VirtualFile>,
                           explicitProfiles: MavenExplicitProfiles,
                           locator: MavenProjectReaderProjectLocator,
                           progressReporter: RawProgressReporter,
                           eventHandler: MavenEventHandler,
                           workspaceMap: MavenWorkspaceMap?,
                           updateSnapshots: Boolean): Collection<MavenProjectReaderResult> {
      return runBlockingMaybeCancellable {
        resolveProject(
          reader,
          generalSettings,
          embedder,
          files,
          explicitProfiles,
          locator,
          progressReporter,
          eventHandler,
          workspaceMap,
          updateSnapshots,
          Properties())
      }
    }

    @JvmStatic
    @Throws(MavenProcessCanceledException::class)
    suspend fun resolveProject(reader: MavenProjectReader,
                               generalSettings: MavenGeneralSettings,
                               embedder: MavenEmbedderWrapper,
                               files: Collection<VirtualFile>,
                               explicitProfiles: MavenExplicitProfiles,
                               locator: MavenProjectReaderProjectLocator,
                               progressReporter: RawProgressReporter,
                               eventHandler: MavenEventHandler,
                               workspaceMap: MavenWorkspaceMap?,
                               updateSnapshots: Boolean,
                               userProperties: Properties): Collection<MavenProjectReaderResult> {
      return try {
        val executionResults = embedder.resolveProject(
          files, explicitProfiles, progressReporter, eventHandler, workspaceMap, updateSnapshots, userProperties)
        val filesMap = CollectionFactory.createFilePathMap<VirtualFile>()
        filesMap.putAll(files.associateBy { it.path })
        val readerResults: MutableCollection<MavenProjectReaderResult> = ArrayList()
        for (result in executionResults) {
          val projectData = result.projectData
          if (projectData == null) {
            val file = detectPomFile(filesMap, result)
            MavenLog.LOG.debug("Dependency resolution: projectData is null, file $file")
            if (file != null) {
              val temp: MavenProjectReaderResult = reader.readProjectAsync(generalSettings, file, explicitProfiles, locator)
              temp.readingProblems.addAll(result.problems)
              temp.unresolvedArtifactIds.addAll(result.unresolvedArtifacts)
              readerResults.add(temp)
              MavenLog.LOG.debug("Dependency resolution: projectData is null, read project")
            }
          }
          else {
            readerResults.add(MavenProjectReaderResult(
              projectData.mavenModel,
              projectData.mavenModelMap,
              MavenExplicitProfiles(projectData.activatedProfiles, explicitProfiles.disabledProfiles),
              projectData.nativeMavenProject,
              result.problems,
              result.unresolvedArtifacts,
              result.unresolvedProblems))
          }
        }
        readerResults
      }
      catch (e: MavenProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        MavenLog.LOG.info(e)
        MavenLog.printInTests(e) // print exception since we need to know if something wrong with our logic
        files.map {
          val result: MavenProjectReaderResult = reader.readProjectAsync(generalSettings, it, explicitProfiles, locator)
          val message = e.message
          if (message != null) {
            result.readingProblems.add(MavenProjectProblem.createStructureProblem(it.getPath(), message))
          }
          else {
            result.readingProblems.add(MavenProjectProblem.createSyntaxProblem(it.getPath(), MavenProjectProblem.ProblemType.SYNTAX))
          }
          result
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
  }
}