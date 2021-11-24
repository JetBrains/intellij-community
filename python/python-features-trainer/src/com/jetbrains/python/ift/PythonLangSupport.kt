// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.jetbrains.python.statistics.modules
import training.project.ReadMeCreator
import training.util.getFeedbackLink
import java.io.File

class PythonLangSupport : PythonBasedLangSupport() {
  override val contentRootDirectoryName = "PyCharmLearningProject"

  override val primaryLanguage = "Python"

  override val defaultProductName: String = "PyCharm"

  private val sourcesDirectoryName: String = "src"

  override val filename: String = "Learning.py"

  override val langCourseFeedback get() = getFeedbackLink(this, false)

  override val readMeCreator = ReadMeCreator()

  override fun applyToProjectAfterConfigure(): (Project) -> Unit = { project ->
    // mark src directory as sources root
    if (project.modules.size > 1) {
      thisLogger().error("The learning project has more than one module: ${project.modules.map { it.name }}")
    }
    val module = project.modules.first()
    val sourcesPath = project.basePath!! + '/' + sourcesDirectoryName
    val sourcesRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(sourcesPath)
    if (sourcesRoot == null) {
      val status = when {
        !File(sourcesPath).exists() -> "does not exist"
        File(sourcesPath).isDirectory -> "existed directory"
        else -> "it is regular file"
      }
      error("Failed to find directory with source files: $sourcesPath ($status)")
    }
    val rootsModel = ModuleRootManager.getInstance(module).modifiableModel
    val contentEntry = rootsModel.contentEntries.find {
      val contentEntryFile = it.file
      contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, sourcesRoot, false)
    } ?: error("Failed to find content entry for file: ${sourcesRoot.name}")

    contentEntry.addSourceFolder(sourcesRoot, false)
    runWriteAction {
      rootsModel.commit()
      project.save()
    }
  }
}
