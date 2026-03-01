// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.nullability

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.DefaultStateProvider
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.FunctionParameter
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.FunctionReturnType
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.OtherTarget
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.Property
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.TypeArgument
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.TypeElementBasedTypeVariable
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.TypeVariable
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

@K1Deprecation
class NullabilityDefaultStateProvider : DefaultStateProvider() {
    override fun defaultStateFor(typeVariable: TypeVariable): State {
        if (typeVariable is TypeElementBasedTypeVariable
            && typeVariable.typeElement.type.constructor.declarationDescriptor is TypeParameterDescriptor
        ) {
            return State.LOWER
        }
        return when (val owner = typeVariable.owner) {
            is FunctionParameter -> if (owner.owner.isPrivate()) State.LOWER else State.UPPER
            is FunctionReturnType ->
                if (owner.function.isAbstract()
                    || owner.function.hasModifier(KtTokens.OPEN_KEYWORD)
                    || owner.function.bodyExpression == null
                ) State.UPPER else State.LOWER

            is Property -> if (owner.property.isPrivate()
                || !owner.property.isVar
                || !owner.property.isLocal
            ) State.LOWER else State.UPPER

            is TypeArgument -> State.LOWER
            OtherTarget -> State.UPPER
        }
    }
}