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

sealed abstract class MavenImportContext(val project: Project)


class MavenInitialImportContext internal constructor(project: Project,
                                                     val paths: ImportPaths,
                                                     val profiles: MavenExplicitProfiles,
                                                     val generalSettings: MavenGeneralSettings,
                                                     val importingSettings: MavenImportingSettings,
                                                     val indicator: MavenProgressIndicator) : MavenImportContext(project)


class MavenReadContext internal constructor(project: Project,
                                            val projectsTree: MavenProjectsTree,
                                            val toResolve: Collection<MavenProject>,
                                            val withSyntaxErrors: Collection<MavenProject>,
                                            val initialContext: MavenInitialImportContext) : MavenImportContext(project) {
  fun hasReadingProblems() = !withSyntaxErrors.isEmpty()
  fun collectProblems(): Collection<MavenProjectProblem> = withSyntaxErrors.flatMap { it.problems }

}

class MavenResolvedContext internal constructor(project: Project,
                                                val unresolvedArtifacts: Collection<MavenArtifact>,
                                                val projectsToImport: List<MavenProject>,
                                                val nativeProjectHolder: List<Pair<MavenProject, NativeMavenProjectHolder>>,
                                                val readContext: MavenReadContext) : MavenImportContext(project) {
  val initialContext = readContext.initialContext
}

class MavenPluginResolvedContext internal constructor(project: Project,
                                                      val unresolvedPlugins: Map<MavenPlugin, Path?>,
                                                      val resolvedContext: MavenResolvedContext) : MavenImportContext(project)

class MavenSourcesGeneratedContext internal constructor(val resolvedContext: MavenResolvedContext, val projectsFoldersResolved: ArrayList<MavenProject>) : MavenImportContext(resolvedContext.project)
class MavenImportedContext internal constructor(project: Project,
                                                val modulesCreated: MutableList<Module>,
                                                val postImportTasks: MutableList<MavenProjectsProcessorTask>?,
                                                val initialContext: MavenInitialImportContext) : MavenImportContext(project)

class MavenImportingExtensionsContext internal constructor(project: Project) : MavenImportContext(project)
class MavenImportFinishedContext internal constructor(context: MavenImportContext) : MavenImportContext(context.project)


sealed class ImportPaths
class FilesList(val poms: List<VirtualFile>) : ImportPaths()
class RootPath(val path: VirtualFile) : ImportPaths()