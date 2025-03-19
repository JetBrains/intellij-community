// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import training.project.ProjectUtils
import training.project.ReadMeCreator
import training.util.getFeedbackLink

class PythonLangSupport : PythonBasedLangSupport() {
  override val contentRootDirectoryName = "PyCharmLearningProject"

  override val primaryLanguage = "Python"

  override val defaultProductName: String = "PyCharm"

  private val sourcesDirectoryName: String = "src"

  override val scratchFileName: String = "Learning.py"

  override val langCourseFeedback get() = getFeedbackLink(this, false)

  override val readMeCreator = ReadMeCreator()

  override fun applyToProjectAfterConfigure(): (Project) -> Unit = { project ->
    ProjectUtils.markDirectoryAsSourcesRoot(project, sourcesDirectoryName)
  }

  override fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean = true
}
