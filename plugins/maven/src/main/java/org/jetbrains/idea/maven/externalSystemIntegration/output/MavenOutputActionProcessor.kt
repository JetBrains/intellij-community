// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.FileContentUtil
import com.intellij.util.SmartList
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil

import java.io.File

class MavenOutputActionProcessor(private val myProject: Project, private val myWorkingDir: String) {

  fun showMavenInvalidConfig(message: String) {
    val manager = MavenProjectsManager.getInstance(myProject)
    val mavenProject = manager.rootProjects.filter {
      it.directoryFile == LocalFileSystem.getInstance().findFileByIoFile(File(myWorkingDir))
    }.firstOrNull()

    if (mavenProject == null) {
      MavenLog.LOG.warn("Cannot find appropriate maven project,project =  ${myProject.name}, workingdir = ${myWorkingDir}")
      return
    }

    MavenProjectsTree.showNotificationInvalidConfig(myProject, mavenProject, message)
    mavenProject.configFileError = message

    MavenUtil.restartConfigHighlightning(myProject, SmartList(mavenProject))
  }
}
