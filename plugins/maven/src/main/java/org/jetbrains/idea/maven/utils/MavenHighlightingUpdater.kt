// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.*
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenProjectsTree

@Service(Service.Level.PROJECT)
internal class MavenHighlightingUpdater(
  private val project: Project,
  private val coroutineScope: CoroutineScope
) : Disposable {

  fun install(projectsManager: MavenProjectsManager) {
    projectsManager.addManagerListener(object : MavenProjectsManager.Listener {
      override fun activated() {
        schedule(null)
      }
    }, this)

    projectsManager.addProjectsTreeListener(object : MavenProjectsTree.Listener {
      override fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>,
                                   deleted: List<MavenProject>) {
        for (each in updated) {
          schedule(each.first)
        }
      }

      override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
        schedule(projectWithChanges.first)
      }

      override fun pluginsResolved(mavenProject: MavenProject) {
        schedule(mavenProject)
      }

      override fun foldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
        schedule(projectWithChanges.first)
      }

      override fun artifactsDownloaded(mavenProject: MavenProject) {
        schedule(mavenProject)
      }
    })
  }

  @Synchronized
  fun schedule(mavenProject: MavenProject?) {
    val children = coroutineScope.coroutineContext.job.children.toList()
    for (child in children) {
      child.cancel()
    }

    val mavenFile = mavenProject?.file
    val noChildren = children.isEmpty()

    coroutineScope.launch {
      delay(1000)

      if (noChildren && mavenFile != null) {
        // update highlighting in a single file
        rehighlight(mavenFile)
      }
      else {
        val files = withContext(Dispatchers.EDT) {
          FileEditorManager.getInstance(project).openFiles
        }
        for (file in files) {
          rehighlight(file)
        }
      }
    }
  }

  private suspend fun rehighlight(file: VirtualFile) {
    readAction {
      if (!file.isValid) return@readAction null

      if (!FileTypeManager.getInstance().isFileOfType(file, XmlFileType.INSTANCE)) {
        return@readAction null
      }

      val doc = FileDocumentManager.getInstance().getCachedDocument(file)
      if (doc == null) return@readAction null

      val psiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(doc)
      if (psiFile == null) return@readAction null

      if (!MavenDomUtil.isMavenFile(psiFile)) return@readAction null

      DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
    }
  }

  override fun dispose() {
    // do nothing
  }
}