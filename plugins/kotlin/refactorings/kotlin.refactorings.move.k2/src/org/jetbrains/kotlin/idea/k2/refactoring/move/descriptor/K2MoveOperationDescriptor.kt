// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveFilesOrDirectoriesRefactoringProcessor
import org.jetbrains.kotlin.psi.KtNamedDeclaration

sealed class K2MoveOperationDescriptor<T : K2MoveDescriptor>(
    val project: Project,
    val moveDescriptors: List<T>,
    val searchForText: Boolean,
    val searchInComments: Boolean,
    val searchReferences: Boolean
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
        searchReferences: Boolean
    ) : K2MoveOperationDescriptor<K2MoveDescriptor.Files>(project, moveDescriptors, searchForText, searchInComments, searchReferences) {
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
        searchReferences: Boolean
    ) : K2MoveOperationDescriptor<K2MoveDescriptor.Declarations>(project, moveDescriptors, searchForText, searchInComments, searchReferences) {
        override val sourceElements: List<KtNamedDeclaration> get() = moveDescriptors.flatMap { it.source.elements }

        override fun refactoringProcessor(): BaseRefactoringProcessor {
            return K2MoveDeclarationsRefactoringProcessor(this)
        }
    }
}