// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import java.util.concurrent.CompletableFuture

class ChooseAnotherJdkQuickFix : BuildIssueQuickFix {
  override val id: String = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val sdkPopupFactory = ApplicationManager.getApplication().service<SdkPopupFactory>()
    val result = CompletableFuture<Void>()
    val popup = sdkPopupFactory.createBuilder()
      .withProject(project)
      .withSdkFilter { it ->
        it.getSdkType() is JavaSdkType && JavaSdkVersionUtil.isAtLeast(it, JavaSdkVersion.JDK_17)
      }.onSdkSelected { sdk ->
        val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
        cs.launch {
          val table = ProjectJdkTable.getInstance()

          if (table.findJdk(sdk.name) == null) {
            writeAction {
              table.addJdk(sdk)
            }
          }
          val settings = MavenWorkspaceSettingsComponent.getInstance(project).settings
          settings.importingSettings.jdkForImporter = sdk.name
          val runnerSettings = MavenRunner.getInstance(project).settings;
          runnerSettings.setJreName(sdk.name)
          MavenProjectsManager.getInstance(project).scheduleUpdateAllMavenProjects(MavenSyncSpec.full("ChooseAnotherJdkQuickFix", true))
        }
      }.onPopupClosed {
        result.complete(null)
      }.buildPopup()

    popup.showCenteredInCurrentWindow(project)
    return result
  }

  companion object {
    const val ID = "choose_another_maven_jdk_quick_fix"
  }
}