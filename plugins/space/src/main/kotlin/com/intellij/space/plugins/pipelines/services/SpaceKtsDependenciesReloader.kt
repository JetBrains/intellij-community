// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import com.intellij.space.vcs.PostStartupActivity
import libraries.klogging.logger
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.psi.KtFile

private val LOG = logger<SpaceKtsDependenciesReloader>()

class SpaceKtsDependenciesReloaderActivator : PostStartupActivity() {
  override fun runActivity(project: Project) {
    project.service<SpaceKtsDependenciesReloader>()
  }
}

@Service
internal class SpaceKtsDependenciesReloader(private val project: Project) : LifetimedDisposable by LifetimedDisposableImpl() {
  init {
    SpaceWorkspaceComponent.getInstance().workspace.forEach(lifetime) { workspace ->
      if (workspace != null) {
        val ktsFile = project.service<SpaceKtsFileDetector>().dslFile.value
        if (ktsFile != null) {
          reload(ktsFile)
        }
      }
    }
  }

  private fun reload(ktsFile: VirtualFile) {
    val psiFile = PsiManager.getInstance(project).findFile(ktsFile) ?: return
    try {
      val scriptingSupport = DefaultScriptingSupport.getInstance(psiFile.project)
      scriptingSupport.updateScriptDefinitionsReferences() // TODO: don't revalidate caches of all script definitions here
      scriptingSupport.ensureUpToDatedConfigurationSuggested(psiFile as KtFile, true)
    }
    catch (th: Throwable) {
      LOG.info(th) { "Couldn't reload .space.kts dependencies" }
    }
  }
}