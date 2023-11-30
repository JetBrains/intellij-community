// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.openapi.project.Project
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveFilesOrDirectoriesRefactoringProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveMembersRefactoringProcessor

sealed class K2MoveDescriptor(
    open val project: Project,
    open val source: K2MoveSourceDescriptor<*>,
    open val target: K2MoveTargetDescriptor,
    val searchForText: Boolean,
    val searchInComments: Boolean,
    val searchReferences: Boolean
) {
    abstract fun refactoringProcessor(): BaseRefactoringProcessor

    /**
     * A file preserved move is when a user moves 1 or more files into a directory. All the files will be preserved here, they will just
     * be moved into a different location.
     */
    class Files(
        override val project: Project,
        override val source: K2MoveSourceDescriptor.FileSource,
        override val target: K2MoveTargetDescriptor.SourceDirectory,
        searchForText: Boolean,
        searchInComments: Boolean,
        searchReferences: Boolean
    ) : K2MoveDescriptor(project, source, target, searchForText, searchInComments, searchReferences) {
        override fun refactoringProcessor(): BaseRefactoringProcessor {
            return K2MoveFilesOrDirectoriesRefactoringProcessor(this)
        }
    }

    /**
     * A file member moves is when the user moves the members inside the files, not considering the files themselves.
     */
    class Members(
        override val project: Project,
        override val source: K2MoveSourceDescriptor.ElementSource,
        override val target: K2MoveTargetDescriptor.File,
        searchForText: Boolean,
        searchInComments: Boolean,
        searchReferences: Boolean
    ) : K2MoveDescriptor(project, source, target, searchForText, searchInComments, searchReferences) {
        override fun refactoringProcessor(): BaseRefactoringProcessor {
            return K2MoveMembersRefactoringProcessor(this)
        }
    }
}