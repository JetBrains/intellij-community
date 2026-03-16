// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

@K1Deprecation
abstract class StateUpdater {
    fun updateStates(inferenceContext: InferenceContext) {
        if (inferenceContext.typeVariables.isEmpty()) return

        val deepComparator = Comparator<TypeElementBasedTypeVariable> { o1, o2 ->
            if (o1.typeElement.typeElement.isAncestor(o2.typeElement.typeElement)) 1 else -1
        }
        val typeVariablesSortedByDeep = inferenceContext.typeVariables
            .filterIsInstance<TypeElementBasedTypeVariable>()
            .sortedWith(deepComparator)

        runWriteAction {
            for (typeVariable in typeVariablesSortedByDeep) {
                typeVariable.updateState()
            }
        }
    }

    abstract fun TypeElementBasedTypeVariable.updateState()
}