// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtNamedDeclaration

@ApiStatus.Internal
/**
 * Currently only implemented for K2.
 * [beforeMove] and [afterMove] are called in a single write action with the same argument.
 */
interface MoveDeclarationsToFileRefactoringListener {
    fun beforeMove(moveDescriptor: MoveDescriptor)
    fun afterMove(moveDescriptor: MoveDescriptor)

    data class MoveDescriptor(
        val project: Project,
        val elements: Collection<KtNamedDeclaration>,
        val targetFileName: String,
        val targetBaseDirectory: PsiDirectory,
    )

    companion object {
        val TOPIC: Topic<MoveDeclarationsToFileRefactoringListener> = Topic.create<MoveDeclarationsToFileRefactoringListener>(
            MoveDeclarationsToFileRefactoringListener::class.java.simpleName,
            MoveDeclarationsToFileRefactoringListener::class.java
        )
    }
}
