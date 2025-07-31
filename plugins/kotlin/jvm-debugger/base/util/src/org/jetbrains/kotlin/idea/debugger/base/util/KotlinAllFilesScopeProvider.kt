// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope

@Service(Service.Level.PROJECT)
class KotlinAllFilesScopeProvider(private val project: Project) {
    companion object {
        fun getInstance(project: Project): KotlinAllFilesScopeProvider = project.service()
    }

    fun getAllKotlinFilesScope(): GlobalSearchScope =
        object : DelegatingGlobalSearchScope(
            KotlinSourceFilterScope.projectAndLibrarySources(GlobalSearchScope.allScope(project), project)
        ) {
            private val projectIndex = ProjectRootManager.getInstance(this@KotlinAllFilesScopeProvider.project).fileIndex
            private val scopeComparator = compareBy<VirtualFile> { projectIndex.isInSourceContent(it) }
                .thenComparing { file: VirtualFile -> projectIndex.isInLibrarySource(file) }
                .thenComparing { file1: VirtualFile, file2: VirtualFile -> super.compare(file1, file2) }

            override fun compare(file1: VirtualFile, file2: VirtualFile): Int = scopeComparator.compare(file1, file2)
        }
}
