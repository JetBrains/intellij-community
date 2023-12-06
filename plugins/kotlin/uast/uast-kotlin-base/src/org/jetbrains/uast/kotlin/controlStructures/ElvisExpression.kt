// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

private fun createVariableReferenceExpression(variable: UVariable, containingElement: UElement?) =
    object : USimpleNameReferenceExpression {
        override val psi: PsiElement? = null
        override fun resolve(): PsiElement = variable
        override val uastParent: UElement? = containingElement
        override val resolvedName: String? = variable.name
        override val uAnnotations: List<UAnnotation> = emptyList()
        override val identifier: String = variable.name.orAnonymous()
        override val javaPsi: PsiElement? = null
        override val sourcePsi: PsiElement? = null
    }

private fun createNullLiteralExpression(containingElement: UElement?) =
    object : ULiteralExpression {
        override val psi: PsiElement? = null
        override val uastParent: UElement? = containingElement
        override val value: Any? = null
        override val uAnnotations: List<UAnnotation> = emptyList()
        override val javaPsi: PsiElement? = null
        override val sourcePsi: PsiElement? = null
    }

private fun createNotEqWithNullExpression(variable: UVariable, containingElement: UElement?) =
    object : UBinaryExpression {
        private val leftOperandPart = UastLazyPart<UExpression>()
        private val rightOperandPart = UastLazyPart<UExpression>()

        override val psi: PsiElement? = null
        override val uastParent: UElement? = containingElement
        override val leftOperand: UExpression
            get() = leftOperandPart.getOrBuild {
                createVariableReferenceExpression(variable, this)
            }
        override val rightOperand: UExpression
            get() = rightOperandPart.getOrBuild {
                createNullLiteralExpression(this)
            }
        override val operator: UastBinaryOperator = UastBinaryOperator.NOT_EQUALS
        override val operatorIdentifier: UIdentifier = KotlinUIdentifier(null, this)
        override fun resolveOperator(): PsiMethod? = null
        override val uAnnotations: List<UAnnotation> = emptyList()
        override val javaPsi: PsiElement? = null
        override val sourcePsi: PsiElement? = null
    }

private fun createElvisExpressions(
    left: KtExpression,
    right: KtExpression,
    containingElement: UElement?,
    psiParent: PsiElement
): List<UExpression> {
    val declaration = KotlinUDeclarationsExpression(containingElement)
    val tempVariable = KotlinULocalVariable(UastKotlinPsiVariable.create(left, declaration, psiParent), null, declaration)
    declaration.declarations = listOf(tempVariable)

    val ifExpression = object : UIfExpression {
        private val conditionPart = UastLazyPart<UExpression>()
        private val thenExpressionPart = UastLazyPart<UExpression?>()
        private val elseExpressionPart = UastLazyPart<UExpression?>()

        override val psi: PsiElement? = null
        override val uastParent: UElement? = containingElement
        override val javaPsi: PsiElement? = null
        override val sourcePsi: PsiElement? = null

        override val condition: UExpression
            get() = conditionPart.getOrBuild {
                createNotEqWithNullExpression(tempVariable, this)
            }

        override val thenExpression: UExpression?
            get() = thenExpressionPart.getOrBuild {
                createVariableReferenceExpression(tempVariable, this)
            }

        override val elseExpression: UExpression?
            get() = elseExpressionPart.getOrBuild {
                val service = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
                service.baseKotlinConverter.convertExpression(right, this, DEFAULT_EXPRESSION_TYPES_LIST)
            }

        override val isTernary: Boolean = false
        override val uAnnotations: List<UAnnotation> = emptyList()
        override val ifIdentifier: UIdentifier = KotlinUIdentifier(null, this)
        override val elseIdentifier: UIdentifier = KotlinUIdentifier(null, this)
    }

    return listOf(declaration, ifExpression)
}

fun createElvisExpression(elvisExpression: KtBinaryExpression, givenParent: UElement?): UExpression {
    val left = elvisExpression.left ?: return UastEmptyExpression(givenParent)
    val right = elvisExpression.right ?: return UastEmptyExpression(givenParent)

    return KotlinUElvisExpression(elvisExpression, left, right, givenParent)
}

@ApiStatus.Internal
class KotlinUElvisExpression(
    private val elvisExpression: KtBinaryExpression,
    private val left: KtExpression,
    private val right: KtExpression,
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UExpressionList, KotlinEvaluatableUElement {

    private val expressionsPart = UastLazyPart<List<UExpression>>()

    override val javaPsi: PsiElement? = null
    override val sourcePsi: PsiElement = elvisExpression
    override val psi: PsiElement = sourcePsi
    override val kind = KotlinSpecialExpressionKinds.ELVIS

    override val uAnnotations: List<UAnnotation>
        get() {
            val annotatedExpression = sourcePsi.parent as? KtAnnotatedExpression ?: return emptyList()
            return annotatedExpression.annotationEntries.mapNotNull { languagePlugin?.convertOpt(it, this) }
        }

    override val expressions: List<UExpression>
        get() = expressionsPart.getOrBuild {
            createElvisExpressions(left, right, this, elvisExpression.parent)
        }

    val lhsDeclaration: UDeclaration
        get() = (expressions[0] as UDeclarationsExpression).declarations.single()
    val rhsIfExpression: UIfExpression
        get() = expressions[1] as UIfExpression

    override fun asRenderString(): String {
        return kind.name + " " +
                expressions.joinToString(separator = "\n", prefix = "{\n", postfix = "\n}") {
                    it.asRenderString().withMargin
                }
    }

    override fun getExpressionType(): PsiType? {
        return baseResolveProviderService.getCommonSupertype(left, right, this)
    }
}
