// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider
import java.lang.Thread.sleep

class LinkMavenProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "linkMavenProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    //val manager = MavenProjectsManager.getInstanceIfCreated(project)!!


    //val singlePomSelection = object : FileChooserDescriptor(true, true, false, false, false, false) {
    //  override fun isFileSelectable(file: VirtualFile?): Boolean {
    //    return super.isFileSelectable(file) && !manager.isManagedFile(file!!)
    //  }
    //
    //  override fun isFileVisible(file: VirtualFile, showHiddenFiles: Boolean): Boolean {
    //    return if (!file.isDirectory && !MavenActionUtil.isMavenProjectFile(file)) false else super.isFileVisible(file, showHiddenFiles)
    //  }
    //}

    val filePath = extractCommandArgument(PREFIX)
    val projectPomFile = findFile(extractCommandArgument(PREFIX), project)

    if (projectPomFile == null) {
      throw IllegalArgumentException("File not found: $filePath")
    }

    sleep(95000)
    val openProjectProvider = MavenOpenProjectProvider()
    openProjectProvider.linkToExistingProjectAsync(projectPomFile, project)
    sleep(5000)
  }

  override fun getName(): String {
    return NAME
  }
}