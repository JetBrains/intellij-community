// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import org.jetbrains.idea.maven.buildtool.MavenLogEventHandler
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.project.MavenProjectResolver.Companion.getInstance
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.IOException
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

abstract class MavenProjectsTreeTestCase : MavenMultiVersionImportingTestCase() {
  private var myTree: MavenProjectsTree? = null
  protected val rawProgressReporter: RawProgressReporter = object : RawProgressReporter {}

  val tree: MavenProjectsTree
    get() {
      return myTree!!
    }

  @Throws(Exception::class)
  override fun setUpInWriteAction() {
    super.setUpInWriteAction()
    myTree = MavenProjectsManager.getInstance(project).getProjectsTree()
  }

  protected suspend fun updateAll(vararg files: VirtualFile?) {
    updateAll(emptyList<String>(), *files)
  }

  protected suspend fun updateAll(profiles: List<String?>?, vararg files: VirtualFile?) {
    myTree!!.resetManagedFilesAndProfiles(listOf(*files), MavenExplicitProfiles(profiles))
    myTree!!.updateAll(false, mavenGeneralSettings, rawProgressReporter)
  }

  protected suspend fun update(file: VirtualFile) {
    myTree!!.update(listOf(file), false, mavenGeneralSettings, rawProgressReporter)
  }

  protected suspend fun deleteProject(file: VirtualFile) {
    myTree!!.delete(listOf(file), mavenGeneralSettings, rawProgressReporter)
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

    override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>,
                                 nativeMavenProject: NativeMavenProjectHolder?) {
      add("resolved", java.util.Set.of(projectWithChanges.first.mavenId.artifactId))
    }

    override fun pluginsResolved(project: MavenProject) {
      add("plugins", setOf(project.mavenId.artifactId))
    }

    override fun foldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
      add("folders", setOf(projectWithChanges.first.mavenId.artifactId))
    }
  }

  @Throws(MavenProcessCanceledException::class)
  protected fun resolve(project: Project,
                        mavenProject: MavenProject,
                        generalSettings: MavenGeneralSettings,
                        embeddersManager: MavenEmbeddersManager,
                        process: MavenProgressIndicator) {
    val resolver = getInstance(project)
    val progressReporter = object : RawProgressReporter {}
    runBlockingMaybeCancellable {
      resolver.resolve(listOf(mavenProject),
                       myTree!!,
                       generalSettings,
                       embeddersManager,
                       progressReporter,
                       MavenLogEventHandler)
    }
  }

  companion object {
    @JvmStatic
    protected fun log(): ListenerLog {
      return ListenerLog()
    }
  }
}
