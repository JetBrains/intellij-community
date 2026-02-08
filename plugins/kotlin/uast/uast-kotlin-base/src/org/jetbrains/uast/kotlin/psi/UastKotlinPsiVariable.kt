// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.impl.light.LightTypeElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.LightVariableBuilder
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UNINITIALIZED_UAST_PART
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.KotlinUDeclarationsExpression
import org.jetbrains.uast.kotlin.orAnonymous

@ApiStatus.Internal
class UastKotlinPsiVariable private constructor(
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

    private var psiTypeElementPart: Any? = UNINITIALIZED_UAST_PART
    private var psiInitializerPart: Any? = UNINITIALIZED_UAST_PART

    private val psiParent: PsiElement? by lazy(LazyThreadSafetyMode.NONE, psiParentProducer)
    private val psiType: PsiType by lazy(LazyThreadSafetyMode.NONE, typeProducer)

    private val psiTypeElement: PsiTypeElement
        get() {
            if (psiTypeElementPart == UNINITIALIZED_UAST_PART) {
                psiTypeElementPart = LightTypeElement(manager, psiType)
            }
            return psiTypeElementPart as PsiTypeElement
        }

    private val psiInitializer: PsiExpression?
        get() {
            if (psiInitializerPart == UNINITIALIZED_UAST_PART) {
                psiInitializerPart = ktInitializer?.let { KotlinUastPsiExpression(it, containingElement) }
            }
            return psiInitializerPart as PsiExpression?
        }

    override fun getType(): PsiType = psiType

    override fun getText(): String = ktElement.text

    override fun getTextRange(): TextRange = ktElement.textRange

    override fun getParent(): PsiElement? = psiParent

    override fun hasInitializer(): Boolean = ktInitializer != null

    override fun getInitializer(): PsiExpression? = psiInitializer

    override fun getTypeElement(): PsiTypeElement = psiTypeElement

    override fun setInitializer(initializer: PsiExpression?): Nothing = throw NotImplementedError()

    override fun getContainingFile(): PsiFile? = ktElement.containingFile

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class.java != this::class.java) return false
        return ktElement == (other as? UastKotlinPsiVariable)?.ktElement
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean = this == another || ktElement.isEquivalentTo(another)

    override fun hashCode(): Int = ktElement.hashCode()

    companion object {
        fun create(
            declaration: KtVariableDeclaration,
            parent: PsiElement?,
            containingElement: KotlinUDeclarationsExpression,
            initializer: KtExpression? = null
        ): PsiLocalVariable {
            val psi = containingElement.psiAnchor ?: containingElement.sourcePsi
            val psiParent = psi?.getNonStrictParentOfType<KtDeclaration>() ?: parent
            val initializerExpression = initializer ?: declaration.initializer
            return UastKotlinPsiVariable(
                manager = declaration.manager,
                name = declaration.name.orAnonymous("<unnamed>"),
                typeProducer = {
                    val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
                    service.getType(declaration, containingElement) ?: UastErrorType
                },
                ktInitializer = initializerExpression,
                psiParentProducer = { psiParent },
                containingElement = containingElement,
                ktElement = declaration
            )
        }

        fun create(
            declaration: KtDestructuringDeclaration,
            containingElement: UElement
        ): PsiLocalVariable =
            UastKotlinPsiVariable(
                manager = declaration.manager,
                name = "var" + Integer.toHexString(declaration.getHashCode()),
                typeProducer = {
                    val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
                    service.getType(declaration, containingElement) ?: UastErrorType
                },
                ktInitializer = declaration.initializer,
                psiParentProducer = { containingElement.getParentOfType<UDeclaration>()?.psi ?: declaration.parent },
                containingElement = containingElement,
                ktElement = declaration
            )

        fun create(
            initializer: KtExpression,
            containingElement: UElement,
            parent: PsiElement
        ): PsiLocalVariable =
            UastKotlinPsiVariable(
                manager = initializer.manager,
                name = "var" + Integer.toHexString(initializer.getHashCode()),
                typeProducer = {
                    val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
                    service.getType(initializer, containingElement) ?: UastErrorType
                },
                ktInitializer = initializer,
                psiParentProducer = { containingElement.getParentOfType<UDeclaration>()?.psi ?: parent },
                containingElement = containingElement,
                ktElement = initializer
            )

        fun create(
            name: String,
            localFunction: KtFunction,
            containingElement: UElement
        ): PsiLocalVariable =
            UastKotlinPsiVariable(
                manager = localFunction.manager,
                name = name,
                typeProducer = {
                    val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
                    service.getFunctionType(localFunction, containingElement) ?: UastErrorType
                },
                ktInitializer = localFunction,
                psiParentProducer = { containingElement.getParentOfType<UDeclaration>()?.psi ?: localFunction.parent },
                containingElement = containingElement,
                ktElement = localFunction
            )
    }
}

private class KotlinUastPsiExpression(
    val ktExpression: KtExpression,
    val parent: UElement
) : PsiElement by ktExpression, PsiExpression {
    override fun getType(): PsiType? {
        val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
        return service.getType(ktExpression, parent)
    }
}

private fun PsiElement.getHashCode(): Int {
    var result = 42
    result = 41 * result + (containingFile?.name?.hashCode() ?: 0)
    result = 41 * result + startOffset
    result = 41 * result + text.hashCode()
    return result
}
