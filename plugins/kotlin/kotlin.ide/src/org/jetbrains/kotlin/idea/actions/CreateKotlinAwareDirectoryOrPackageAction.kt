// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.actions.CreateDirectoryOrPackageAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.base.util.KOTLIN_SOURCE_ROOT_TYPES

class CreateKotlinAwareDirectoryOrPackageAction: CreateDirectoryOrPackageAction(true) {
    override fun isPackage(project: Project, directories: List<PsiDirectory>): Boolean {
        if (super.isPackage(project, directories)) return true

        val fileIndex = ProjectRootManager.getInstance(project).getFileIndex()
        return directories.any {
            val virtualFile = it.getVirtualFile()
            fileIndex.isUnderSourceRootOfType(virtualFile, KOTLIN_SOURCE_ROOT_TYPES)
        }
    }
}