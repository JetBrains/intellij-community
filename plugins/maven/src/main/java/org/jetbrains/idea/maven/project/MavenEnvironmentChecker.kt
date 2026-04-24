// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.buildtool.quickfix.ChooseAnotherJdkQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenImportingSettingsQuickFix
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.utils.MavenUtil

@ApiStatus.Internal
class MavenEnvironmentChecker(val console: MavenSyncConsole, val project: Project) {
  suspend fun checkEnvironment(managedFiles: List<VirtualFile>): Boolean {
    val mavenVersions = managedFiles.map { MavenDistributionsCache.getInstance(project).getMavenVersion(it) }
      .toSet()
    checkJdkVersions(mavenVersions)?.let {
      console.addBuildIssue(it, MessageEvent.Kind.ERROR)
      return false
    }
    return true
  }

  private suspend fun checkJdkVersions(mavenVersions: Set<String?>): BuildIssue? {
    val jdk = MavenUtil.getJdkForImporter(project)
    mavenVersions.forEach {
      checkJdkCompatibility(it, jdk)?.let { return it }
    }
    return null
  }


  companion object {
    suspend fun checkJdkCompatibility(mavenVersion: String?, jdk: Sdk): BuildIssue? {
      if (JavaSdkVersionUtil.isAtLeast(jdk, JavaSdkVersion.JDK_17)) return null
      if (mavenVersion?.startsWith("4.") == true) return WrongJdkVersion("4", JavaSdkVersion.JDK_17)
      if (!JavaSdkVersionUtil.isAtLeast(jdk, JavaSdkVersion.JDK_1_8)) return WrongJdkVersion("3", JavaSdkVersion.JDK_1_8)
      return null
    }
  }
}

private class WrongJdkVersion(val mavenVer: String, val expectedJdkVersion: JavaSdkVersion) : BuildIssue {
  override val title: @BuildEventsNls.Title String = SyncBundle.message("maven.sync.jdk.title")
  override val description: @BuildEventsNls.Description String = SyncBundle.message(
    "maven.sync.jdk.description",
    mavenVer,
    expectedJdkVersion.description,
    ChooseAnotherJdkQuickFix.ID,
    OpenMavenImportingSettingsQuickFix.ID,
  )

  override val quickFixes = listOf(
    OpenMavenImportingSettingsQuickFix(),
    ChooseAnotherJdkQuickFix()
  )

  override fun getNavigatable(project: Project): Navigatable? = null

}