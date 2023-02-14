// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.KotlinFakeUElement
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

fun expressionTypes(requiredType: Class<out UElement>?) =
    requiredType?.let { arrayOf(it) } ?: DEFAULT_EXPRESSION_TYPES_LIST

fun elementTypes(requiredType: Class<out UElement>?) =
    requiredType?.let { arrayOf(it) } ?: DEFAULT_TYPES_LIST

fun <T : UElement> Array<out Class<out T>>.nonEmptyOr(default: Array<out Class<out UElement>>) =
    takeIf { it.isNotEmpty() } ?: default

inline fun <reified ActualT : UElement> Array<out Class<out UElement>>.el(f: () -> UElement?): UElement? {
    return if (isAssignableFrom(ActualT::class.java)) f() else null
}

inline fun <reified ActualT : UElement> Array<out Class<out UElement>>.expr(f: () -> UExpression?): UExpression? {
    return if (isAssignableFrom(ActualT::class.java)) f() else null
}

fun Array<out Class<out UElement>>.isAssignableFrom(cls: Class<*>) = any { it.isAssignableFrom(cls) }

val identifiersTokens = setOf(
    KtTokens.IDENTIFIER, KtTokens.CONSTRUCTOR_KEYWORD, KtTokens.OBJECT_KEYWORD,
    KtTokens.THIS_KEYWORD, KtTokens.SUPER_KEYWORD,
    KtTokens.GET_KEYWORD, KtTokens.SET_KEYWORD
)

fun UElement.toSourcePsiFakeAware(): List<PsiElement> {
    if (this is KotlinFakeUElement) return this.unwrapToSourcePsi()
    return listOfNotNull(this.sourcePsi)
}

fun wrapExpressionBody(function: UElement, bodyExpression: KtExpression): UExpression? = when (bodyExpression) {
    !is KtBlockExpression -> {
        KotlinLazyUBlockExpression(function) { block ->
            val implicitReturn = KotlinUImplicitReturnExpression(block)
            val uBody = function.getLanguagePlugin().convertElement(bodyExpression, implicitReturn) as? UExpression
                ?: return@KotlinLazyUBlockExpression emptyList()
            listOf(implicitReturn.apply { returnExpression = uBody })
        }

    }
    else -> function.getLanguagePlugin().convertElement(bodyExpression, function) as? UExpression
}

fun reportConvertFailure(psiMethod: PsiMethod): Nothing {
    val isValid = psiMethod.isValid
    val report = KotlinExceptionWithAttachments(
        "cant convert $psiMethod of ${psiMethod.javaClass} to UMethod" + if (!isValid) " (method is not valid)" else ""
    )

    if (isValid) {
        report.withAttachment("method", psiMethod.text)
        psiMethod.containingFile?.let {
            report.withAttachment("file", it.text)
        }
    }

    throw report
}

fun createKDocNameSimpleNameReference(parentKDocName: KDocName, givenParent: UElement?): USimpleNameReferenceExpression? =
    parentKDocName.lastChild?.let { psiIdentifier ->
        parentKDocName.getQualifiedName().lastOrNull()?.let { qualifierText ->
            KotlinStringUSimpleReferenceExpression(qualifierText, givenParent, psiIdentifier, parentKDocName)
        }
    }

fun createLocalFunctionDeclaration(function: KtFunction, parent: UElement?): UDeclarationsExpression {
    return KotlinUDeclarationsExpression(null, parent, function).apply {
        val functionVariable = UastKotlinPsiVariable.create(function.name.orAnonymous(), function, this)
        declarations = listOf(KotlinLocalFunctionUVariable(function, functionVariable, this))
    }
}

fun createLocalFunctionLambdaExpression(function: KtFunction, parent: UElement?): ULambdaExpression =
    KotlinLocalFunctionULambdaExpression(function, parent)
