// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference.ShorteningMode
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier

interface IReplacement<KotlinType> : Function2<IExtractableCodeDescriptor<KotlinType>, KtElement, KtElement>

interface ParameterReplacement<KotlinType> : IReplacement<KotlinType> {
    val parameter: IParameter<KotlinType>
    fun copy(parameter: IParameter<KotlinType>): ParameterReplacement<KotlinType>
}

class RenameReplacement<KotlinType>(override val parameter: IParameter<KotlinType>) : ParameterReplacement<KotlinType> {
    override fun copy(parameter: IParameter<KotlinType>) = RenameReplacement(parameter)

    override fun invoke(descriptor: IExtractableCodeDescriptor<KotlinType>, e: KtElement): KtElement {
        val expressionToReplace = (e.parent as? KtThisExpression ?: e).let { it.getQualifiedExpressionForSelector() ?: it }
        val parameterName = KtPsiUtil.unquoteIdentifier(parameter.nameForRef)
        val replacingName =
            if (e.text.startsWith('`') || !parameterName.isIdentifier()) "`$parameterName`" else parameterName
        val psiFactory = KtPsiFactory(e.project)
        val replacement = when {
            parameter == descriptor.receiverParameter -> psiFactory.createExpression("this")
            expressionToReplace is KtOperationReferenceExpression -> psiFactory.createOperationName(replacingName)
            else -> psiFactory.createSimpleName(replacingName)
        }
        return expressionToReplace.replaced(replacement)
    }
}

abstract class WrapInWithReplacement<KotlinType> : IReplacement<KotlinType> {
    abstract val argumentText: String

    override fun invoke(descriptor: IExtractableCodeDescriptor<KotlinType>, e: KtElement): KtElement {
        val call = (e as? KtSimpleNameExpression)?.getQualifiedElement() ?: return e
        val replacingExpression = KtPsiFactory(e.project).createExpressionByPattern("with($0) { $1 }", argumentText, call)
        val replace = call.replace(replacingExpression)
        return (replace as KtCallExpression).lambdaArguments.first().getLambdaExpression()!!.bodyExpression!!.statements.first()
    }
}

class WrapParameterInWithReplacement<KotlinType>(override val parameter: IParameter<KotlinType>) : WrapInWithReplacement<KotlinType>(),
                                                                                                   ParameterReplacement<KotlinType> {
    override val argumentText: String
        get() = parameter.name

    override fun copy(parameter: IParameter<KotlinType>) = WrapParameterInWithReplacement(parameter)
}


class AddPrefixReplacement<KotlinType>(override val parameter: IParameter<KotlinType>) : ParameterReplacement<KotlinType> {
    override fun copy(parameter: IParameter<KotlinType>) = AddPrefixReplacement(parameter)

    override fun invoke(descriptor: IExtractableCodeDescriptor<KotlinType>, e: KtElement): KtElement {
        if (descriptor.receiverParameter == parameter) return e

        val selector = (e.parent as? KtCallExpression) ?: e
        val replacingExpression = KtPsiFactory(e.project).createExpressionByPattern("${parameter.nameForRef}.$0", selector)
        val newExpr = (selector.replace(replacingExpression) as KtQualifiedExpression).selectorExpression!!
        return (newExpr as? KtCallExpression)?.calleeExpression ?: newExpr
    }
}

class FqNameReplacement<KotlinType>(val fqName: FqName) : IReplacement<KotlinType> {
    override fun invoke(descriptor: IExtractableCodeDescriptor<KotlinType>, e: KtElement): KtElement {
        val thisExpr = e.parent as? KtThisExpression
        if (thisExpr != null) {
            return thisExpr.replaced(KtPsiFactory(e.project).createExpression(fqName.asString())).getQualifiedElementSelector()!!
        }

        val newExpr = (e as? KtSimpleNameExpression)?.mainReference?.bindToFqName(fqName, ShorteningMode.NO_SHORTENING) as KtElement
        return if (newExpr is KtQualifiedExpression) newExpr.selectorExpression!! else newExpr
    }
}
