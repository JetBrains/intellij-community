package com.intellij.space.plugins.pipelines.utils

import circlet.pipelines.DefaultDslFileName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

object DslFileFinder {

  fun find(project: Project): VirtualFile? {
    val basePath = project.basePath ?: return null
    val baseDirFile = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
    return find(baseDirFile)
  }

  fun checkFileNameIsApplicable(name: String): Boolean {
    return DefaultDslFileName.equals(name, true)
  }

  private fun find(baseDirFile: VirtualFile): VirtualFile? {
    return baseDirFile.children.firstOrNull {
      checkFileNameIsApplicable(it.name)
    }
  }
}
