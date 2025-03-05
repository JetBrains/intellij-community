// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.jetbrains.idea.maven.buildtool.MavenLogEventHandler
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

abstract class MavenProjectsTreeTestCase : MavenMultiVersionImportingTestCase() {
  private var myTree: MavenProjectsTree? = null
  protected val rawProgressReporter: RawProgressReporter = object : RawProgressReporter {}
  protected lateinit var mavenEmbedderWrappers: MavenEmbedderWrappers

  val tree: MavenProjectsTree
    get() {
      return myTree!!
    }

  @Throws(Exception::class)
  override fun setUpInWriteAction() {
    super.setUpInWriteAction()
    myTree = MavenProjectsManager.getInstance(project).getProjectsTree()
  }

  override fun setUp() {
    super.setUp()
    mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { mavenEmbedderWrappers.close() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  protected suspend fun updateAll(vararg files: VirtualFile) {
    updateAll(emptyList<String>(), *files)
  }

  protected suspend fun updateAll(profiles: List<String?>?, vararg files: VirtualFile) {
    tree.resetManagedFilesAndProfiles(listOf(*files), MavenExplicitProfiles(profiles))
    tree.updateAll(false, mavenGeneralSettings, mavenEmbedderWrappers, rawProgressReporter)
  }

  protected suspend fun update(file: VirtualFile) {
    tree.update(listOf(file), false, mavenGeneralSettings, mavenEmbedderWrappers, rawProgressReporter)
  }

  protected suspend fun deleteProject(file: VirtualFile) {
    tree.delete(listOf(file), mavenGeneralSettings, mavenEmbedderWrappers, rawProgressReporter)
  }

  @Throws(IOException::class)
  protected fun updateTimestamps(vararg files: VirtualFile) {
    WriteAction.runAndWait<IOException> {
      for (each in files) {
        each.setBinaryContent(each.contentsToByteArray())
      }
    }
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

  protected class MyLoggingListener : MavenProjectsTree.Listener {
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

    override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
      add("resolved", setOf(projectWithChanges.first.mavenId.artifactId))
    }

    override fun pluginsResolved(project: MavenProject) {
      add("plugins", setOf(project.mavenId.artifactId))
    }

    override fun foldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
      add("folders", setOf(projectWithChanges.first.mavenId.artifactId))
    }
  }

  protected suspend fun resolve(project: Project,
                                mavenProject: MavenProject,
                                generalSettings: MavenGeneralSettings
  ) {
    val resolver = MavenProjectResolver(project)
    val progressReporter = object : RawProgressReporter {}
    val updateSnapshots = projectsManager.forceUpdateSnapshots || generalSettings.isAlwaysUpdateSnapshots
    resolver.resolve(true,
                     listOf(mavenProject),
                     tree,
                     tree.workspaceMap,
                     generalSettings.effectiveRepositoryPath,
                     updateSnapshots,
                     mavenEmbedderWrappers,
                     progressReporter,
                     MavenLogEventHandler)
  }

  companion object {
    @JvmStatic
    protected fun log(): ListenerLog {
      return ListenerLog()
    }
  }
}
