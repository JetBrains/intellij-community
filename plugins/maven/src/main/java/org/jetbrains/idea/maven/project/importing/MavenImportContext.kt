// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.nio.file.Path

sealed abstract class MavenImportContext(val project: Project, val indicator: MavenProgressIndicator)


class MavenStartedImport(project: Project) : MavenImportContext(project, MavenProgressIndicator(project, null))
class MavenInitialImportContext internal constructor(project: Project,
                                                     val paths: ImportPaths,
                                                     val profiles: MavenExplicitProfiles,
                                                     val generalSettings: MavenGeneralSettings,
                                                     val importingSettings: MavenImportingSettings,
                                                     indicator: MavenProgressIndicator
) : MavenImportContext(project, indicator)


class MavenReadContext internal constructor(project: Project,
                                            val projectsTree: MavenProjectsTree,
                                            val toResolve: Collection<MavenProject>,
                                            val withSyntaxErrors: Collection<MavenProject>,
                                            val initialContext: MavenInitialImportContext) : MavenImportContext(project,
                                                                                                                initialContext.indicator) {
  fun hasReadingProblems() = !withSyntaxErrors.isEmpty()
  fun collectProblems(): Collection<MavenProjectProblem> = withSyntaxErrors.flatMap { it.problems }

}

class MavenResolvedContext internal constructor(project: Project,
                                                val unresolvedArtifacts: Collection<MavenArtifact>,
                                                val projectsToImport: List<MavenProject>,
                                                val nativeProjectHolder: List<Pair<MavenProject, NativeMavenProjectHolder>>,
                                                val readContext: MavenReadContext) : MavenImportContext(project, readContext.indicator) {
  val initialContext = readContext.initialContext
}

class MavenPluginResolvedContext internal constructor(project: Project,
                                                      val unresolvedPlugins: Map<MavenPlugin, Path?>,
                                                      val resolvedContext: MavenResolvedContext) : MavenImportContext(project,
                                                                                                                      resolvedContext.indicator)

class MavenSourcesGeneratedContext internal constructor(val resolvedContext: MavenResolvedContext,
                                                        val projectsFoldersResolved: ArrayList<MavenProject>) : MavenImportContext(
  resolvedContext.project, resolvedContext.indicator)

class MavenImportedContext internal constructor(project: Project,
                                                val modulesCreated: List<Module>,
                                                val postImportTasks: List<MavenProjectsProcessorTask>?,
                                                val readContext: MavenReadContext) : MavenImportContext(project,
                                                                                                        readContext.indicator)

class MavenImportFinishedContext internal constructor(val context: MavenImportedContext?,
                                                      val error: Throwable?,
                                                      project: Project,
                                                      indicator: MavenProgressIndicator) : MavenImportContext(project, indicator) {
  constructor(e: Throwable, project: Project) : this(null, e, project, MavenProgressIndicator(project, null))
  constructor(context: MavenImportedContext) : this(context, null, context.project, context.indicator)
}


sealed class ImportPaths
class FilesList(val poms: List<VirtualFile>) : ImportPaths(){
  constructor(poms: Array<VirtualFile>) : this(poms.asList())
  constructor(pom: VirtualFile) : this(listOf(pom))
}
class RootPath(val path: VirtualFile) : ImportPaths()