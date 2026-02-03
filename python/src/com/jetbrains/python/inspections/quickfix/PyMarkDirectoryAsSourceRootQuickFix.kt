// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.module.PySourceRootDetectionService
import java.io.File
import javax.swing.Icon

internal class PyMarkDirectoryAsSourceRootQuickFix(
  private val project: Project,
  private val sourceRoot: VirtualFile
) : LocalQuickFix, Iconable {
  override fun startInWriteAction(): Boolean = true

  override fun getFamilyName(): @IntentionFamilyName String {
    return PyPsiBundle.message("QFIX.add.source.root.for.unresolved.import.family.name")
  }

  override fun getName(): @IntentionName String {
    return PyPsiBundle.message("QFIX.add.source.root.for.unresolved.import.name", getPathName())
  }

  private fun getPathName(): @NlsSafe String {
    val projectDir = project.guessProjectDir() ?: return sourceRoot.name
    val relativePath = VfsUtilCore.getRelativePath(sourceRoot, projectDir, File.separatorChar)
    return relativePath?.replace("\\", "/") ?: sourceRoot.name
  }

  override fun getIcon(flags: Int): Icon {
    return AllIcons.Modules.SourceRoot
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    project.getService(PySourceRootDetectionService::class.java).markAsSourceRoot(sourceRoot, showNotification = true)
  }
}
