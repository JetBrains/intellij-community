// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty

internal open class UastFakeSourceLightAccessorBase<T: KtDeclaration>(
    internal val property: KtProperty,
    original: T,
    containingClass: PsiClass,
    protected val isSetter: Boolean,
) : UastFakeSourceLightMethodBase<T>(original, containingClass) {

    override fun getName(): String {
        val propertyName = property.name ?: ""
        // TODO: what about @JvmName w/ use-site target?
        return if (isSetter) JvmAbi.setterName(propertyName) else JvmAbi.getterName(propertyName)
    }

    override fun getReturnType(): PsiType? {
        if (isSetter) {
            return PsiTypes.voidType()
        }

        return super.getReturnType()
    }

}