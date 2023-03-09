// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.nullability

import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.StateUpdater
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.TypeElementBasedTypeVariable
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeElement

class NullabilityStateUpdater : StateUpdater() {
    override fun TypeElementBasedTypeVariable.updateState() = when (state) {
        State.LOWER -> changeState(toNullable = false)
        State.UPPER -> changeState(toNullable = true)
        else -> Unit
    }

    private fun TypeElementBasedTypeVariable.changeState(toNullable: Boolean) {
        changeState(typeElement.typeElement, toNullable)
    }

    companion object {
        fun changeState(typeElement: KtTypeElement, toNullable: Boolean) {
            val psiFactory = KtPsiFactory(typeElement.project)
            if (typeElement is KtNullableType && !toNullable) {
                typeElement.replace(psiFactory.createType(typeElement.innerType?.text ?: return).typeElement ?: return)
            }
            if (typeElement !is KtNullableType && toNullable) {
                typeElement.replace(psiFactory.createType("${typeElement.text}?").typeElement ?: return)
            }
        }
    }

}