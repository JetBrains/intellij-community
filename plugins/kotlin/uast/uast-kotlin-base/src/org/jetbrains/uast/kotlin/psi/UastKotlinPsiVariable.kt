// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightTypeElement
import org.jetbrains.kotlin.asJava.elements.LightVariableBuilder
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.*

class UastKotlinPsiVariable private constructor(
    private val baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
    manager: PsiManager,
    name: String,
    typeProducer: () -> PsiType,
    val ktInitializer: KtExpression?,
    psiParentProducer: () -> PsiElement?,
    val containingElement: UElement,
    val ktElement: KtElement
) : LightVariableBuilder(
    manager,
    name,
    UastErrorType, // Type is calculated lazily
    KotlinLanguage.INSTANCE
), PsiLocalVariable {

    val psiParent by lz(psiParentProducer)

    private val psiType: PsiType by lz(typeProducer)

    private val psiTypeElement: PsiTypeElement by lz {
        LightTypeElement(manager, psiType)
    }

    private val psiInitializer: PsiExpression? by lz {
        ktInitializer?.let { KotlinUastPsiExpression(baseKotlinUastResolveProviderService, it, containingElement) }
    }

    override fun getType(): PsiType = psiType

    override fun getText(): String = ktElement.text

    override fun getParent() = psiParent

    override fun hasInitializer() = ktInitializer != null

    override fun getInitializer(): PsiExpression? = psiInitializer

    override fun getTypeElement() = psiTypeElement

    override fun setInitializer(initializer: PsiExpression?) = throw NotImplementedError()

    override fun getContainingFile(): PsiFile? = ktElement.containingFile

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class.java != this::class.java) return false
        return ktElement == (other as? UastKotlinPsiVariable)?.ktElement
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean = this == another || ktElement.isEquivalentTo(another)

    override fun hashCode() = ktElement.hashCode()

    companion object {
        fun create(
            baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
            declaration: KtVariableDeclaration,
            parent: PsiElement?,
            containingElement: KotlinUDeclarationsExpression,
            initializer: KtExpression? = null
        ): PsiLocalVariable {
            val psi = containingElement.psiAnchor ?: containingElement.sourcePsi
            val psiParent = psi?.getNonStrictParentOfType<KtDeclaration>() ?: parent
            val initializerExpression = initializer ?: declaration.initializer
            return UastKotlinPsiVariable(
                baseKotlinUastResolveProviderService,
                manager = declaration.manager,
                name = declaration.name.orAnonymous("<unnamed>"),
                typeProducer = { baseKotlinUastResolveProviderService.getType(declaration, containingElement) ?: UastErrorType },
                ktInitializer = initializerExpression,
                psiParentProducer = { psiParent },
                containingElement = containingElement,
                ktElement = declaration)
        }

        fun create(
            baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
            declaration: KtDestructuringDeclaration,
            containingElement: UElement
        ): PsiLocalVariable =
                UastKotlinPsiVariable(
                    baseKotlinUastResolveProviderService,
                    manager = declaration.manager,
                    name = "var" + Integer.toHexString(declaration.getHashCode()),
                    typeProducer = { baseKotlinUastResolveProviderService.getType(declaration, containingElement) ?: UastErrorType },
                    ktInitializer = declaration.initializer,
                    psiParentProducer = { containingElement.getParentOfType<UDeclaration>()?.psi ?: declaration.parent },
                    containingElement = containingElement,
                    ktElement = declaration
                )

        fun create(
            baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
            initializer: KtExpression,
            containingElement: UElement,
            parent: PsiElement
        ): PsiLocalVariable =
                UastKotlinPsiVariable(
                    baseKotlinUastResolveProviderService,
                    manager = initializer.manager,
                    name = "var" + Integer.toHexString(initializer.getHashCode()),
                    typeProducer = { baseKotlinUastResolveProviderService.getType(initializer, containingElement) ?: UastErrorType },
                    ktInitializer = initializer,
                    psiParentProducer = { containingElement.getParentOfType<UDeclaration>()?.psi ?: parent },
                    containingElement = containingElement,
                    ktElement = initializer
                )

        fun create(
            baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
            name: String,
            localFunction: KtFunction,
            containingElement: UElement
        ): PsiLocalVariable =
                UastKotlinPsiVariable(
                    baseKotlinUastResolveProviderService,
                    manager = localFunction.manager,
                    name = name,
                    typeProducer = {
                        baseKotlinUastResolveProviderService.getFunctionType(localFunction, containingElement) ?: UastErrorType
                    },
                    ktInitializer = localFunction,
                    psiParentProducer = { containingElement.getParentOfType<UDeclaration>()?.psi ?: localFunction.parent },
                    containingElement = containingElement,
                    ktElement = localFunction
                )
    }
}

private class KotlinUastPsiExpression(
    private val baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
    val ktExpression: KtExpression,
    val parent: UElement
) : PsiElement by ktExpression, PsiExpression {
    override fun getType(): PsiType? = baseKotlinUastResolveProviderService.getType(ktExpression, parent)
}

private fun PsiElement.getHashCode(): Int {
    var result = 42
    result = 41 * result + (containingFile?.name?.hashCode() ?: 0)
    result = 41 * result + startOffset
    result = 41 * result + text.hashCode()
    return result
}
