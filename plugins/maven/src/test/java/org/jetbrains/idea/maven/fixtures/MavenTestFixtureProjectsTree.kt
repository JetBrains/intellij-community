// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import org.jetbrains.idea.maven.buildtool.MavenLogEventHandler
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenEmbedderWrappers
import org.jetbrains.idea.maven.project.MavenEmbedderWrappersManager
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectResolver
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

// Ported from MavenProjectsTreeTestCase.

val MavenImportingTestFixture.tree: MavenProjectsTree
  get() = projectsManager.projectsTree

val MavenImportingTestFixture.rawProgressReporter: RawProgressReporter
  get() = object : RawProgressReporter {}

private val EMBEDDER_WRAPPERS_KEY = Key.create<MavenEmbedderWrappers>("test.maven.fixture.embedderWrappers")

// Per-test embedder wrappers: created lazily on first use, cached on the (per-test) project, and closed via the
// fixture's disposable. Kept self-contained here so MavenDomTestFixtureImpl carries no extra state.
val MavenImportingTestFixture.mavenEmbedderWrappers: MavenEmbedderWrappers
  get() {
    project.getUserData(EMBEDDER_WRAPPERS_KEY)?.let { return it }
    val wrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    project.putUserData(EMBEDDER_WRAPPERS_KEY, wrappers)
    Disposer.register(disposable, Disposable {
      project.putUserData(EMBEDDER_WRAPPERS_KEY, null)
      wrappers.close()
    })
    return wrappers
  }

suspend fun MavenImportingTestFixture.updateAll(vararg files: VirtualFile) {
  updateAll(emptyList<String>(), *files)
}

suspend fun MavenImportingTestFixture.updateAll(profiles: List<String>, vararg files: VirtualFile) {
  tree.updateAll(listOf(*files), false,
                 mavenGeneralSettings, MavenExplicitProfiles(profiles, emptySet()), mavenEmbedderWrappers, rawProgressReporter)
}

suspend fun MavenImportingTestFixture.update(file: VirtualFile) {
  tree.update(listOf(file), false, mavenGeneralSettings, MavenExplicitProfiles.NONE, mavenEmbedderWrappers, rawProgressReporter)
}

suspend fun MavenImportingTestFixture.deleteProject(file: VirtualFile) {
  tree.delete(listOf(file), mavenGeneralSettings, MavenExplicitProfiles.NONE, mavenEmbedderWrappers, rawProgressReporter)
}

@Throws(IOException::class)
fun MavenImportingTestFixture.updateTimestamps(vararg files: VirtualFile) {
  WriteAction.runAndWait<IOException> {
    for (each in files) {
      each.setBinaryContent(each.contentsToByteArray())
    }
  }
}

suspend fun MavenImportingTestFixture.resolve(
  project: Project,
  mavenProject: MavenProject,
  generalSettings: MavenGeneralSettings,
) {
  val resolver = MavenProjectResolver(project)
  val progressReporter = object : RawProgressReporter {}
  val updateSnapshots = projectsManager.forceUpdateSnapshots || generalSettings.isAlwaysUpdateSnapshots
  resolver.resolve(true,
                   listOf(mavenProject),
                   tree,
                   MavenExplicitProfiles.NONE,
                   tree.workspaceMap,
                   MavenSettingsCache.getInstance(project).getEffectiveUserLocalRepo(),
                   updateSnapshots,
                   mavenEmbedderWrappers,
                   progressReporter,
                   MavenLogEventHandler)
}

class ListenerLog : CopyOnWriteArrayList<Pair<String, Set<String>>> {
  internal constructor() : super()
  internal constructor(log: ListenerLog) : super(log)

  fun add(key: String, vararg values: String): ListenerLog {
    val log = ListenerLog(this)
    log.add(Pair<String, Set<String>>(key, setOf(*values)))
    return log
  }
}

class MyLoggingListener : MavenProjectsTree.Listener {
  @JvmField
  var log: MutableList<Pair<String, Set<String?>>> = CopyOnWriteArrayList()

  private fun add(key: String, value: Set<String?>) {
    log.add(Pair(key, value))
  }

  override fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
    append(MavenUtil.collectFirsts(updated), "updated")
    append(deleted, "deleted")
  }

  private fun append(updated: List<MavenProject>, text: String) {
    add(text, updated.map { it.mavenId.artifactId }.toSet())
  }

  override fun projectsResolved(projects: List<MavenProject>) {
    for (project in projects) {
      add("resolved", setOf(project.mavenId.artifactId))
    }
  }

  override fun pluginsResolved(projects: List<MavenProject>) {
    for (project in projects) {
      add("plugins", setOf(project.mavenId.artifactId))
    }
  }

  override fun foldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
    add("folders", setOf(projectWithChanges.first.mavenId.artifactId))
  }
}

fun log(): ListenerLog = ListenerLog()
