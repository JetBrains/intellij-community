// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool.quickfix

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.MavenVersionSupportUtil
import org.jetbrains.idea.maven.project.MavenProjectBundle
import java.util.concurrent.CompletableFuture


class InstallMaven2BuildIssue : BuildIssue {
  override val title = getTitle()
  override val description = getDescription()
  override val quickFixes = listOf(InstallMaven2QuickFix(), EnableMaven2QuickFix())
  override fun getNavigatable(project: Project): Navigatable? = null

  companion object {
    private fun getTitle(): String {
      if (MavenVersionSupportUtil.isMaven2PluginDisabled)
        return MavenProjectBundle.message("label.invalid.enable.maven2plugin")
      else
        return MavenProjectBundle.message("label.invalid.install.maven2plugin")
    }

    private fun getDescription(): String {
      if (MavenVersionSupportUtil.isMaven2PluginDisabled)
        return MavenProjectBundle.message("label.invalid.enable.maven2plugin.with.link", EnableMaven2QuickFix.ID)
      else
        return MavenProjectBundle.message("label.invalid.install.maven2plugin.with.link", InstallMaven2QuickFix.ID)
    }
  }

}


private const val MAVEN2_SEARCH_STRING = "Maven2 Support"

class InstallMaven2QuickFix : BuildIssueQuickFix {
  override val id = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    ApplicationManager.getApplication().invokeLater {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable::class.java) {
        it.openMarketplaceTab(MAVEN2_SEARCH_STRING)
      }
    }
    return CompletableFuture.completedFuture(null)
  }

  companion object {
    const val ID = "install_maven_2_quick_fix"
  }
}

class EnableMaven2QuickFix : BuildIssueQuickFix {
  override val id = ID

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    ApplicationManager.getApplication().invokeLater {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable::class.java) {
        it.openInstalledTab(MAVEN2_SEARCH_STRING)
      }
    }
    return CompletableFuture.completedFuture(null)
  }

  companion object {
    const val ID = "enable_maven_2_quick_fix"
  }

}