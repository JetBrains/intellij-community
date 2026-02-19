// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.refactoring.ui

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox
import org.jetbrains.kotlin.idea.base.util.collectKotlinAwareDestinationSourceRoots
import org.jetbrains.kotlin.idea.base.util.getKotlinAwareDestinationSourceRoots

abstract class KotlinDestinationFolderComboBox : DestinationFolderComboBox() {
    protected open fun sourceRootsInTargetDirOnly(): Boolean = false

    override fun getSourceRoots(project: Project, initialTargetDirectory: PsiDirectory?): List<VirtualFile> {
        if (sourceRootsInTargetDirOnly()) {
            if (initialTargetDirectory != null) {
                val module = ModuleUtilCore.findModuleForFile(initialTargetDirectory.virtualFile, project)
                if (module != null) {
                    val moduleSourceRoots = module.collectKotlinAwareDestinationSourceRoots()
                    return moduleSourceRoots.filter { root -> targetDirIsInRoot(initialTargetDirectory, root) }
                }
            }
        }
        return project.getKotlinAwareDestinationSourceRoots()
    }

    companion object {
        private fun targetDirIsInRoot(targetDir: PsiDirectory, root: VirtualFile): Boolean {
            return targetDir.virtualFile.path.startsWith(root.path)
        }
    }
}