// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.MavenProgressIndicator


data class MavenImportingResult(
  val finishPromise: Promise<MavenImportFinishedContext>,
  val vfsRefreshPromise: Promise<Any?>?,
  val previewModulesCreated: Module?
)

abstract sealed class MavenImportContext(val project: Project) {
  abstract val indicator: MavenProgressIndicator
}


class MavenStartedImport(project: Project) : MavenImportContext(project) {
  override val indicator = MavenProgressIndicator(project, null)
}

class MavenInitialImportContext internal constructor(project: Project,
                                                     val paths: ImportPaths,
                                                     val profiles: MavenExplicitProfiles,
                                                     val generalSettings: MavenGeneralSettings,
                                                     val importingSettings: MavenImportingSettings,
                                                     val ignorePaths: List<String>,
                                                     val ignorePatterns: List<String>,
                                                     val importDisposable: Disposable,
                                                     val previewModule: Module?,
                                                     val startImportStackTrace: Exception
) : MavenImportContext(project) {
  override val indicator = MavenProgressIndicator(project, null)
}

data class WrapperData(val distributionUrl: String, val baseDir: VirtualFile)

class MavenReadContext internal constructor(project: Project,
                                            val projectsTree: MavenProjectsTree,
                                            val toResolve: Collection<MavenProject>,
                                            private val withSyntaxErrors: Collection<MavenProject>,
                                            val initialContext: MavenInitialImportContext,
                                            val wrapperData: WrapperData?,
                                            override val indicator: MavenProgressIndicator) : MavenImportContext(project) {
  fun hasReadingProblems() = !withSyntaxErrors.isEmpty()
  fun collectProblems(): Collection<MavenProjectProblem> = withSyntaxErrors.flatMap { it.problems }

}

class MavenResolvedContext internal constructor(project: Project,
                                                val unresolvedArtifacts: Collection<MavenArtifact>,
                                                val projectsToImport: List<MavenProject>,
                                                val nativeProjectHolder: List<Pair<MavenProject, NativeMavenProjectHolder>>,
                                                val readContext: MavenReadContext) : MavenImportContext(project) {
  val initialContext = readContext.initialContext
  override val indicator = readContext.indicator
}

class MavenPluginResolvedContext internal constructor(project: Project,
                                                      val unresolvedPlugins: Set<MavenPlugin>,
                                                      private val resolvedContext: MavenResolvedContext) : MavenImportContext(project) {
  override val indicator = resolvedContext.indicator
}


class MavenSourcesGeneratedContext internal constructor(private val resolvedContext: MavenResolvedContext,
                                                        val projectsFoldersResolved: List<MavenProject>) : MavenImportContext(
  resolvedContext.project) {
  override val indicator = resolvedContext.indicator
}

class MavenImportedContext internal constructor(project: Project,
                                                val modulesCreated: List<Module>,
                                                val postImportTasks: List<MavenProjectsProcessorTask>?,
                                                val readContext: MavenReadContext,
                                                val resolvedContext: MavenResolvedContext) : MavenImportContext(project) {
  override val indicator = resolvedContext.indicator
}

class MavenImportFinishedContext internal constructor(val context: MavenImportedContext?,
                                                      val error: Throwable?,
                                                      project: Project) : MavenImportContext(project) {

  override val indicator = context?.indicator ?: MavenProgressIndicator(project, null)

  constructor(e: Throwable, project: Project) : this(null, e, project)
  constructor(context: MavenImportedContext) : this(context, null, context.project)
}


sealed class ImportPaths
class FilesList(val poms: List<VirtualFile>) : ImportPaths() {
  constructor(poms: Array<VirtualFile>) : this(poms.asList())
  constructor(pom: VirtualFile) : this(listOf(pom))
}

class RootPath(val path: VirtualFile) : ImportPaths()