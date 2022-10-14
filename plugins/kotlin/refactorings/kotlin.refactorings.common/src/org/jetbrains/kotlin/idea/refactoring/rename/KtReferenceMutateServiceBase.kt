// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.plugin.references.SimpleNameReferenceExtension
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.astReplace
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.types.expressions.OperatorConventions

abstract class KtReferenceMutateServiceBase : KtReferenceMutateService {
    override fun handleElementRename(ktReference: KtReference, newElementName: String): PsiElement? {
        return when (ktReference) {
            is KtArrayAccessReference -> ktReference.renameTo(newElementName)
            is KDocReference -> ktReference.renameTo(newElementName)
            is KtInvokeFunctionReference -> ktReference.renameTo(newElementName)
            is KtSimpleNameReference -> ktReference.renameTo(newElementName)
            is SyntheticPropertyAccessorReference -> ktReference.renameTo(newElementName)
            is KtDefaultAnnotationArgumentReference -> ktReference.renameTo(newElementName)
            else -> throw IncorrectOperationException()
        }
    }

    protected abstract fun KtArrayAccessReference.renameTo(newElementName: String): KtExpression

    protected abstract fun KDocReference.renameTo(newElementName: String): PsiElement?

    protected abstract fun KtInvokeFunctionReference.renameTo(newElementName: String): PsiElement

    private fun KtSimpleNameReference.renameTo(newElementName: String): KtExpression {
        if (!canRename()) throw IncorrectOperationException()

        if (newElementName.unquoteKotlinIdentifier() == "") {
            return when (val qualifiedElement = expression.getQualifiedElement()) {
                is KtQualifiedExpression -> {
                    expression.replace(qualifiedElement.receiverExpression)
                    qualifiedElement.replaced(qualifiedElement.selectorExpression!!)
                }

                is KtUserType -> expression.replaced(
                    KtPsiFactory(expression).createSimpleName(
                        SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT.asString()
                    )
                )

                else -> expression
            }
        }

        // Do not rename if the reference corresponds to synthesized component function
        val expressionText = expression.text
        if (expressionText != null && Name.isValidIdentifier(expressionText)) {
            if (DataClassResolver.isComponentLike(Name.identifier(expressionText)) && resolve() is KtParameter) {
                return expression
            }
        }

        val psiFactory = KtPsiFactory(expression)
        val element = expression.project.extensionArea.getExtensionPoint(SimpleNameReferenceExtension.EP_NAME).extensions
            .asSequence()
            .map { it.handleElementRename(this, psiFactory, newElementName) }
            .firstOrNull { it != null } ?: psiFactory.createNameIdentifier(newElementName.quoteIfNeeded())

        val nameElement = expression.getReferencedNameElement()

        val elementType = nameElement.node.elementType
        if (elementType is KtToken && OperatorConventions.getNameForOperationSymbol(elementType) != null) {
            val opExpression = expression.parent as? KtOperationExpression
            if (opExpression != null) {
                val (newExpression, newNameElement) = convertOperatorToFunctionCall(opExpression)
                newNameElement.replace(element)
                return newExpression
            }
        }

        if (element.node.elementType == KtTokens.IDENTIFIER) {
            nameElement.astReplace(element)
        } else {
            nameElement.replace(element)
        }
        return expression
    }

    protected abstract fun SyntheticPropertyAccessorReference.renameTo(newElementName: String): KtElement?

    protected abstract fun KtDefaultAnnotationArgumentReference.renameTo(newElementName: String): KtValueArgument

    /**
     * Converts a call to an operator to a regular explicit function call.
     *
     * @return A pair of resulting function call expression and a [KtSimpleNameExpression] pointing to the function name in that expression.
     */
    protected abstract fun convertOperatorToFunctionCall(opExpression: KtOperationExpression): Pair<KtExpression, KtSimpleNameExpression>
}