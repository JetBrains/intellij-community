// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveCallback
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveFilesOrDirectoriesRefactoringProcessor
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier

sealed class K2MoveOperationDescriptor<T : K2MoveDescriptor>(
    val project: Project,
    val moveDescriptors: List<T>,
    val searchForText: Boolean,
    val searchInComments: Boolean,
    val searchReferences: Boolean,
    val dirStructureMatchesPkg: Boolean,
    val moveCallBack: MoveCallback? = null
) {
    init {
      require(moveDescriptors.isNotEmpty()) { "No move descriptors were provided" }
    }

    abstract val sourceElements: List<PsiElement>

    abstract fun refactoringProcessor(): BaseRefactoringProcessor

    class Files(
        project: Project,
        moveDescriptors: List<K2MoveDescriptor.Files>,
        searchForText: Boolean,
        searchInComments: Boolean,
        searchReferences: Boolean,
        dirStructureMatchesPkg: Boolean,
        moveCallBack: MoveCallback? = null
    ) : K2MoveOperationDescriptor<K2MoveDescriptor.Files>(
        project,
        moveDescriptors,
        searchForText,
        searchInComments,
        searchReferences,
        dirStructureMatchesPkg,
        moveCallBack
    ) {
        override val sourceElements: List<PsiFileSystemItem> get() = moveDescriptors.flatMap { it.source.elements }

        override fun refactoringProcessor(): BaseRefactoringProcessor {
            return K2MoveFilesOrDirectoriesRefactoringProcessor(this)
        }
    }

    class Declarations(
        project: Project,
        moveDescriptors: List<K2MoveDescriptor.Declarations>,
        searchForText: Boolean,
        searchInComments: Boolean,
        searchReferences: Boolean,
        dirStructureMatchesPkg: Boolean,
        moveCallBack: MoveCallback? = null
    ) : K2MoveOperationDescriptor<K2MoveDescriptor.Declarations>(
        project,
        moveDescriptors,
        searchForText,
        searchInComments,
        searchReferences,
        dirStructureMatchesPkg,
        moveCallBack
    ) {
        override val sourceElements: List<KtNamedDeclaration> get() = moveDescriptors.flatMap { it.source.elements }

        override fun refactoringProcessor(): BaseRefactoringProcessor {
            return K2MoveDeclarationsRefactoringProcessor(this)
        }
    }

    companion object {
        @RequiresReadLock
        fun Declarations(
            project: Project,
            declarations: Collection<KtNamedDeclaration>,
            baseDir: PsiDirectory,
            fileName: String,
            pkgName: FqName,
            searchForText: Boolean,
            searchReferences: Boolean,
            searchInComments: Boolean,
            mppDeclarations: Boolean,
            dirStructureMatchesPkg: Boolean,
            moveCallBack: MoveCallback? = null
        ): Declarations {
            if (mppDeclarations && declarations.any { it.isExpectDeclaration() || it.hasActualModifier() }) {
                val descriptors = declarations.flatMap { elem ->
                    ExpectActualUtils.withExpectedActuals(elem).filterIsInstance<KtNamedDeclaration>()
                }.groupBy { elem ->
                    ProjectFileIndex.getInstance(project).getSourceRootForFile(elem.containingFile.virtualFile)?.toPsiDirectory(project)
                }.mapNotNull { (baseDir, elements) ->
                    if (baseDir == null) return@mapNotNull null
                    val srcDescriptor = K2MoveSourceDescriptor.ElementSource(elements)
                    val targetDescriptor = K2MoveTargetDescriptor.File(fileName, pkgName, baseDir)
                    K2MoveDescriptor.Declarations(project, srcDescriptor, targetDescriptor)
                }
                return Declarations(
                    project, descriptors, searchForText, searchReferences, searchInComments, dirStructureMatchesPkg, moveCallBack
                )
            } else {
                val srcDescr = K2MoveSourceDescriptor.ElementSource(declarations)
                val targetDescr = K2MoveTargetDescriptor.File(fileName, pkgName, baseDir)
                val moveDescriptor = K2MoveDescriptor.Declarations(project, srcDescr, targetDescr)
                return Declarations(
                    project, listOf(moveDescriptor), searchForText, searchInComments, searchReferences, dirStructureMatchesPkg, moveCallBack
                )
            }
        }
    }
}