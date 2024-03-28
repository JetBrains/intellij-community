// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.MethodDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

interface KotlinModifiableMethodDescriptor<P : KotlinModifiableParameterInfo, V> : MethodDescriptor<P, V> {
    enum class Kind(val isConstructor: Boolean) {
        FUNCTION(false),
        PRIMARY_CONSTRUCTOR(true),
        SECONDARY_CONSTRUCTOR(true)
    }

    val kind: Kind
        get() {
            val descriptor = baseDeclaration
            return when (descriptor) {
                is KtClass -> Kind.PRIMARY_CONSTRUCTOR
                is KtPrimaryConstructor -> Kind.PRIMARY_CONSTRUCTOR
                is KtSecondaryConstructor -> Kind.SECONDARY_CONSTRUCTOR
                else -> Kind.FUNCTION
            }
        }

    var receiver: P?

    val original: KotlinModifiableMethodDescriptor<P, V>
    val baseDeclaration: PsiElement
}