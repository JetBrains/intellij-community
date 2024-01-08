// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager
import org.jetbrains.idea.maven.utils.MavenDataKeys


class UpdateLocalIndexAction : IndexUpdateAction() {

}

class ShowIndexAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    TODO("Not yet implemented")
  }

}

abstract class IndexUpdateAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val mavenRepo = e.getData(MavenDataKeys.MAVEN_REPOSITORY) ?: return

    val manager = MavenSystemIndicesManager.getInstance()
    MavenSystemIndicesManager.getInstance().cs.launch(Dispatchers.IO + CoroutineName(this.javaClass.name)) {
      manager.startUpdateLuceneIndex(mavenRepo)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}