// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.editor

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.utils.MavenUtil

internal class TabTitlesUpdater(val project: Project) : MavenProjectsTree.Listener {
  override fun projectsUpdated(
    updated: List<Pair<MavenProject, MavenProjectChanges>>,
    deleted: List<MavenProject>,
  ) {
    MavenUtil.invokeLater(project, Runnable {
      for (each in MavenUtil.collectFirsts(updated)) {
        FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(each.file)
      }
    })
  }
}
