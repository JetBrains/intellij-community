// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.k2.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractCodeInliner
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.ExpressionReplacementPerformer
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.collectDescendantsOfType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

class CodeInliner<TCallElement : KtElement>(
    private val usageExpression: KtSimpleNameExpression?,
    private val callElement: TCallElement,
    private val inlineSetter: Boolean,
    codeToInline: CodeToInline
) : AbstractCodeInliner<TCallElement>(callElement, codeToInline) {
    fun doInline(): KtElement? {
        val qualifiedElement = if (callElement is KtExpression) {
            callElement.getQualifiedExpressionForSelector()
                ?: callElement.callableReferenceExpressionForReference()
                ?: callElement
        } else callElement
        val assignment = (qualifiedElement as? KtExpression)
            ?.getAssignmentByLHS()
            ?.takeIf { it.operationToken == KtTokens.EQ }
        val originalDeclaration = codeToInline.originalDeclaration
        val callableForParameters = if (assignment != null && originalDeclaration is KtProperty)
            originalDeclaration.setter?.takeIf { inlineSetter && it.hasBody() } ?: originalDeclaration
        else
            originalDeclaration
        val elementToBeReplaced = assignment.takeIf { callableForParameters is KtPropertyAccessor } ?: qualifiedElement
        val ktFile = elementToBeReplaced.containingKtFile
        for ((fqName, allUnder, alias) in codeToInline.fqNamesToImport) {
            if (fqName.startsWith(FqName.fromSegments(listOf("kotlin")))) {
                //todo https://youtrack.jetbrains.com/issue/KTIJ-25928
                continue
            }
            ktFile.addImport(fqName, allUnder, alias)
        }

        val receiver = usageExpression?.receiverExpression()
        receiver?.marked(USER_CODE_KEY)
        receiver?.mark(RECEIVER_VALUE_KEY)

        if (receiver != null) {
            for (instanceExpression in codeToInline.collectDescendantsOfType<KtInstanceExpressionWithLabel> {
                // for this@ClassName we have only option to keep it as is (although it's sometimes incorrect but we have no other options)
                it is KtThisExpression && !it[CodeToInline.SIDE_RECEIVER_USAGE_KEY] && it.labelQualifier == null
            }) {
                codeToInline.replaceExpression(instanceExpression, receiver)
            }
        }

        processValueParameterUsages(callableForParameters)
        introduceVariablesForParameters(elementToBeReplaced, receiver)

        return ExpressionReplacementPerformer(codeToInline, elementToBeReplaced as KtExpression).doIt { range ->
            val pointers = range.filterIsInstance<KtElement>().map { it.createSmartPointer() }.toList()
            postProcessInsertedCode(pointers)
            PsiChildRange.EMPTY
        }
    }

    private fun introduceVariablesForParameters(elementToBeReplaced: KtElement, receiver: KtExpression?) {
        if (elementToBeReplaced is KtExpression) {
            if (receiver != null) {
                val thisReplaced = codeToInline.collectDescendantsOfType<KtExpression> { it[RECEIVER_VALUE_KEY] }
                if (receiver.shouldKeepValue(usageCount = thisReplaced.size)) {
                    if (thisReplaced.isEmpty()) {
                        codeToInline.statementsBefore.add(0, receiver)
                    } else {
                        //todo create variable and update usages
                    }
                }
            }
        }
    }

    private fun processValueParameterUsages(callableForParameters: KtDeclaration?) {
        if (callableForParameters is KtPropertyAccessor && callableForParameters.isSetter) {
            val parameter = callableForParameters.valueParameters.first()
            val valueAssigned =
                (callElement as? KtExpression)?.getQualifiedExpressionForSelectorOrThis()?.getAssignmentByLHS()?.right ?: return

            val parameterName = parameter.nameAsName
            val usages = codeToInline.collectDescendantsOfType<KtExpression> {
                it[CodeToInline.PARAMETER_USAGE_KEY] == parameterName
            }

            usages.forEach {
                codeToInline.replaceExpression(it, valueAssigned.copied())
            }
        }
    }

    override fun canMoveLambdaOutsideParentheses(expr: KtCallExpression): Boolean {
        return expr.canMoveLambdaOutsideParentheses(skipComplexCalls = false)
    }

    override fun removeRedundantUnitExpressions(pointer: SmartPsiElementPointer<KtElement>) {
        //TODO https://youtrack.jetbrains.com/issue/KTIJ-27433
    }

    override fun removeRedundantLambdasAndAnonymousFunctions(pointer: SmartPsiElementPointer<KtElement>) {
        //TODO("Not yet implemented")
    }

    override fun shortenReferences(pointers: List<SmartPsiElementPointer<KtElement>>): List<KtElement> {
        //todo https://youtrack.jetbrains.com/issue/KT-62676
        val facility = ShortenReferencesFacility.getInstance()
        return pointers.mapNotNull { p ->
            val ktElement = p.element ?: return@mapNotNull null
            facility.shorten(ktElement) as? KtElement
        }
    }

    override fun introduceNamedArguments(pointer: SmartPsiElementPointer<KtElement>) {
        //TODO("Not yet implemented")
    }

    override fun dropArgumentsForDefaultValues(pointer: SmartPsiElementPointer<KtElement>) {
        //TODO("Not yet implemented")
    }

    override fun simplifySpreadArrayOfArguments(pointer: SmartPsiElementPointer<KtElement>) {
        //TODO("Not yet implemented")
    }

    override fun removeExplicitTypeArguments(pointer: SmartPsiElementPointer<KtElement>) {
        //TODO https://youtrack.jetbrains.com/issue/KTIJ-26908
    }
}