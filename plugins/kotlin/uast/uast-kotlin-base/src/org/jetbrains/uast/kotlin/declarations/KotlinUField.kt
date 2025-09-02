// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
open class KotlinUField(
    psi: PsiField,
    override val sourcePsi: KtElement?,
    givenParent: UElement?
) : AbstractKotlinUVariable(givenParent), UFieldEx, PsiField by psi {
    override fun getSourceElement() = sourcePsi ?: this

    override val javaPsi = unwrap<UField, PsiField>(psi)

    override val psi = javaPsi

    override fun getType(): PsiType {
        return delegateExpression?.getExpressionType() ?: javaPsi.type
    }

    override fun getInitializer(): PsiExpression? {
        return super<AbstractKotlinUVariable>.getInitializer()
    }

    override fun getOriginalElement(): PsiElement? {
        return super<AbstractKotlinUVariable>.getOriginalElement()
    }

    override fun getNameIdentifier(): PsiIdentifier {
        return super.getNameIdentifier()
    }

    override fun getContainingFile(): PsiFile {
        return super.getContainingFile()
    }

    override fun isPhysical(): Boolean {
        return true
    }

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitField(this)) return
        uAnnotations.acceptList(visitor)
        uastInitializer?.accept(visitor)
        delegateExpression?.accept(visitor)
        visitor.afterVisitField(this)
    }

    override fun asRenderString(): String = buildString {
        if (uAnnotations.isNotEmpty()) {
            uAnnotations.joinTo(this, separator = " ", postfix = " ") { it.asRenderString() }
        }
        append(javaPsi.renderModifiers())
        // NB: use of (potentially delegated) `type`, instead of `javaPsiInternal.type`, is the only major difference.
        append("var ").append(javaPsi.name).append(": ").append(type.getCanonicalText(false))
        uastInitializer?.let { initializer -> append(" = " + initializer.asRenderString()) }
    }

    private val sourceAnnotationsPart = UastLazyPart<List<UAnnotation>?>()

    override val sourceAnnotations: List<UAnnotation>
        get() = sourceAnnotationsPart.getOrBuild {
            val entries = when (val origin = sourcePsi) {
                    is KtParameter -> origin.annotationEntries
                    is KtProperty -> origin.annotationEntries
                    else -> return@getOrBuild null
            }
            sourceAnnotations(entries)
        } ?: emptyList()

    private fun sourceAnnotations(entries: List<KtAnnotationEntry>): List<UAnnotation> = buildList {
        for (ktAnnotation in entries) {
            add(baseResolveProviderService.baseKotlinConverter.convertAnnotation(ktAnnotation, this@KotlinUField))
        }
    }
}

// copy of internal org.jetbrains.uast.InternalUastUtilsKt.renderModifiers
// original function should be used instead as soon as becomes public
private fun PsiModifierListOwner.renderModifiers(): String {
    val modifiers = PsiModifier.MODIFIERS.filter { hasModifierProperty(it) }.joinToString(" ")
    return if (modifiers.isEmpty()) "" else "$modifiers "
}
