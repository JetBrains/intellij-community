// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor

/**
 * Find all conflicts when moving elements as described by [descriptor].
 */
internal fun findAllMoveConflicts(descriptor: K2MoveDescriptor): MultiMap<PsiElement, String> = MultiMap<PsiElement, String>().apply {
    // TODO collect different type of conflicts
}