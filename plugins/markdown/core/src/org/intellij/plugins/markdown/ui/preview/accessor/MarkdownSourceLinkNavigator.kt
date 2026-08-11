// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.accessor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object MarkdownSourceLinkNavigator {
  @NonNls
  private val SOURCE_LINK = "source://(.+):(\\d+)".toRegex()
  private const val FILE_PATH_GROUP = 1
  private const val OFFSET_GROUP = 2

  fun navigate(project: Project?, link: String, sourceFile: VirtualFile? = null): Boolean {
    val coordinates = parseCoordinates(link) ?: return false
    val targetProject = project?.takeUnless { it.isDisposed }
                        ?: sourceFile?.let { ProjectLocator.getInstance().guessProjectForFile(it) }?.takeUnless { it.isDisposed }
                        ?: return true
    val targetFile = findTargetFile(targetProject, coordinates.filePath) ?: return true
    ApplicationManager.getApplication().invokeLater {
      if (targetProject.isDisposed) return@invokeLater
      val descriptor = OpenFileDescriptor(targetProject, targetFile, coordinates.offset)
      FileEditorManager.getInstance(targetProject).openEditor(descriptor, true)
    }
    return true
  }

  private fun parseCoordinates(link: String): Coordinates? {
    val groups = SOURCE_LINK.matchEntire(link)?.groups ?: return null
    val filePath = groups[FILE_PATH_GROUP]?.value ?: return null
    val offset = groups[OFFSET_GROUP]?.value?.toIntOrNull() ?: return null
    return Coordinates(filePath, offset)
  }

  private fun findTargetFile(project: Project, filePath: String): VirtualFile? {
    return ProjectRootManager.getInstance(project).contentRoots.asSequence()
      .firstNotNullOfOrNull { it.findFileByRelativePath(filePath) }
  }

  private data class Coordinates(val filePath: String, val offset: Int)
}
