// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.nameDeterminant
import org.jetbrains.kotlin.idea.references.AbstractKtReference
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtArrayAccessReference
import org.jetbrains.kotlin.idea.references.KtDefaultAnnotationArgumentReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtReferenceMutateService
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.KtSimpleReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.load.java.propertyNameBySetMethodName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.plugin.references.SimpleNameReferenceExtension
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtOperationExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.createDeclarationByPattern
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.astReplace
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions

abstract class KtReferenceMutateServiceBase : KtReferenceMutateService {
    override fun bindToElement(
        simpleNameReference: KtSimpleNameReference,
        element: PsiElement,
        shorteningMode: KtSimpleNameReference.ShorteningMode
    ): PsiElement {
        val fqName = element.nameDeterminant().kotlinFqName ?: return simpleNameReference.expression
        return bindToFqName(simpleNameReference, fqName, shorteningMode, element)
    }

    protected fun KtSimpleReference<KtNameReferenceExpression>.getAdjustedNewName(newElementName: String): Name? {
        val newNameAsName = Name.identifier(newElementName)
        val newName = if (JvmAbi.isGetterName(newElementName)) {
            propertyNameByGetMethodName(newNameAsName)
        }
        else if (JvmAbi.isSetterName(newElementName)) {
            //TODO: it's not correct
            //TODO: setIsY -> setIsIsY bug
            propertyNameBySetMethodName(
              newNameAsName,
              withIsPrefix = expression.getReferencedName().startsWith("is")
            )
        }
        else null
        return newName
    }

    protected fun KtSimpleReference<KtNameReferenceExpression>.renameToOrdinaryMethod(newElementName: String): KtElement? {
        val psiFactory = KtPsiFactory(expression.project)
        val isGetterRename = isKotlinAwareJavaGetterRename(this)

        val newGetterName = if (isGetterRename) newElementName else JvmAbi.getterName(expression.getReferencedName())

        if (expression.readWriteAccess(false) == ReferenceAccess.READ) {
            return expression.replaced(expression.createCall(psiFactory, newGetterName))
        }

        val newSetterName = if (isGetterRename) JvmAbi.setterName(expression.getReferencedName()) else newElementName

        val fullExpression = expression.getQualifiedExpressionForSelectorOrThis()
        fullExpression.getAssignmentByLHS()?.let { assignment ->
            val rhs = assignment.right ?: return expression
            val operationToken = assignment.operationToken as? KtSingleValueToken ?: return expression
            val counterpartOp = OperatorConventions.ASSIGNMENT_OPERATION_COUNTERPARTS[operationToken]
            val setterArgument = if (counterpartOp != null) {
                val getterCall = if (isGetterRename) fullExpression.createCall(psiFactory, newGetterName) else fullExpression
                psiFactory.createExpressionByPattern("$0 ${counterpartOp.value} $1", getterCall, rhs)
            } else {
                rhs
            }
            val newSetterCall = fullExpression.createCall(psiFactory, newSetterName, setterArgument)
            return assignment.replaced(newSetterCall).getQualifiedElementSelector()
        }

        fullExpression.getStrictParentOfType<KtUnaryExpression>()?.let { unaryExpr ->
            val operationToken = unaryExpr.operationToken as? KtSingleValueToken ?: return expression
            if (operationToken !in OperatorConventions.INCREMENT_OPERATIONS) return expression
            val operationName = OperatorConventions.getNameForOperationSymbol(operationToken)
            val originalValue = if (isGetterRename) fullExpression.createCall(psiFactory, newGetterName) else fullExpression
            val incDecValue = psiFactory.createExpressionByPattern("$0.$operationName()", originalValue)
            val parent = unaryExpr.parent
            val context = parent.parents(true).firstOrNull { it is KtBlockExpression || it is KtDeclarationContainer }
            if (context == parent || context == null) {
                val newSetterCall = fullExpression.createCall(psiFactory, newSetterName, incDecValue)
                return unaryExpr.replaced(newSetterCall).getQualifiedElementSelector()
            } else {
                val anchor = parent.parents(true).firstOrNull { it.parent == context }
                val varName = suggestVariableName(unaryExpr, context)
                val isPrefix = unaryExpr is KtPrefixExpression
                val varInitializer = if (isPrefix) incDecValue else originalValue
                val newVar = psiFactory.createDeclarationByPattern<KtProperty>("val $varName = $0", varInitializer)
                val setterArgument = psiFactory.createExpression(if (isPrefix) varName else "$varName.$operationName()")
                val newSetterCall = fullExpression.createCall(psiFactory, newSetterName, setterArgument)
                val newLine = psiFactory.createNewLine()
                context.addBefore(newVar, anchor)
                context.addBefore(newLine, anchor)
                context.addBefore(newSetterCall, anchor)
                return unaryExpr.replaced(psiFactory.createExpression(varName))
            }
        }

        return expression
    }

    abstract fun KtSimpleReference<KtNameReferenceExpression>.suggestVariableName(expr: KtExpression, context: PsiElement): String

    private fun KtExpression.createCall(
        psiFactory: KtPsiFactory,
        newName: String? = null,
        argument: KtExpression? = null
    ): KtExpression {
        return if (this is KtQualifiedExpression) {
            copied().also {
                val selector = it.getQualifiedElementSelector() as? KtExpression
                selector?.replace(selector.createCall(psiFactory, newName, argument))
            }
        } else {
            psiFactory.buildExpression {
                if (newName != null) {
                    appendFixedText(newName)
                } else {
                    appendExpression(this@createCall)
                }
                appendFixedText("(")
                if (argument != null) {
                    appendExpression(argument)
                }
                appendFixedText(")")
            }
        }
    }

    override fun handleElementRename(ktReference: KtReference, newElementName: String): PsiElement? {
        return when (ktReference) {
            is KtArrayAccessReference -> ktReference.renameTo(newElementName)
            is KDocReference -> ktReference.renameTo(newElementName)
            is KtInvokeFunctionReference -> ktReference.renameTo(newElementName)
            is KtSimpleNameReference -> ktReference.renameTo(newElementName)
            is KtDefaultAnnotationArgumentReference -> ktReference.renameTo(newElementName)
            else -> throw IncorrectOperationException()
        }
    }

    private fun KtArrayAccessReference.renameTo(newElementName: String): KtExpression {
        return renameImplicitConventionalCall(newElementName)
    }

    private fun KDocReference.renameTo(newElementName: String): PsiElement? {
        val textRange = element.getNameTextRange()
        val newText = textRange.replace(element.text, newElementName)
        val newLink = KDocElementFactory(element.project).createNameFromText(newText)
        return element.replace(newLink)
    }

    private fun KtInvokeFunctionReference.renameTo(newElementName: String): PsiElement {
        val callExpression = expression
        val fullCallExpression = callExpression.getQualifiedExpressionForSelectorOrThis()
        if (newElementName == OperatorNameConventions.GET.asString() && callExpression.typeArguments.isEmpty()) {
            val arrayAccessExpression = KtPsiFactory(callExpression.project).buildExpression {
                if (fullCallExpression is KtQualifiedExpression) {
                    appendExpression(fullCallExpression.receiverExpression)
                    appendFixedText(fullCallExpression.operationSign.value)
                }
                appendExpression(callExpression.calleeExpression)
                appendFixedText("[")
                appendExpressions(callExpression.valueArguments.map { it.getArgumentExpression() })
                appendFixedText("]")
            }
            return fullCallExpression.replace(arrayAccessExpression)
        }
        return renameImplicitConventionalCall(newElementName)
    }

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

        val project = expression.project
        val psiFactory = KtPsiFactory(project)
        val nameElement = expression.getReferencedNameElement()
        val elementType = nameElement.node.elementType
        val opExpression = if (elementType is KtToken && OperatorConventions.getNameForOperationSymbol(elementType) != null) {
            expression.parent as? KtOperationExpression
        } else null

        val quotedNewName = newElementName.quoteIfNeeded()
        if (opExpression != null) {
            val (newExpression: KtExpression, newNameElement: KtSimpleNameExpression) = convertOperatorToFunctionCall(opExpression)
            //newNameElement is expression here, should be replaced with expression for psi consistency
            newNameElement.replace(psiFactory.createSimpleName(quotedNewName))
            return newExpression
        }

        val renamedByExtension = project.extensionArea.getExtensionPoint(SimpleNameReferenceExtension.EP_NAME)
            .extensions
            .firstNotNullOfOrNull { it.handleElementRename(this, psiFactory, newElementName) }

        val element = renamedByExtension ?: psiFactory.createNameIdentifier(quotedNewName)
        if (element.node.elementType == KtTokens.IDENTIFIER) {
            nameElement.astReplace(element)
        } else {
            nameElement.replace(element)
        }
        return expression
    }

    private fun KtDefaultAnnotationArgumentReference.renameTo(newElementName: String): KtValueArgument {
        val psiFactory = KtPsiFactory(expression.project)
        val newArgument = psiFactory.createArgument(
          expression.getArgumentExpression(),
          Name.identifier(newElementName.quoteIfNeeded()),
          expression.getSpreadElement() != null
        )
        return expression.replaced(newArgument)
    }

    /**
     * Converts a call to an operator to a regular explicit function call.
     *
     * @return A pair of resulting function call expression and a [KtSimpleNameExpression] pointing to the function name in that expression.
     */
    private fun convertOperatorToFunctionCall(opExpression: KtOperationExpression): Pair<KtExpression, KtSimpleNameExpression> =
        OperatorToFunctionConverter.convert(opExpression)

    abstract fun canMoveLambdaOutsideParentheses(callExpression: KtCallExpression?): Boolean

    protected fun replaceWithImplicitInvokeInvocation(newExpression: KtDotQualifiedExpression): KtExpression? {
        val canMoveLambda = canMoveLambdaOutsideParentheses(newExpression.getPossiblyQualifiedCallExpression())
        return OperatorToFunctionConverter.replaceExplicitInvokeCallWithImplicit(newExpression)?.let { newQualifiedExpression ->
            newQualifiedExpression.getPossiblyQualifiedCallExpression()
                ?.takeIf { canMoveLambda }
                ?.let(KtCallExpression::moveFunctionLiteralOutsideParentheses)

            newQualifiedExpression
        }
    }

    private fun AbstractKtReference<out KtExpression>.renameImplicitConventionalCall(newName: String): KtExpression {
        val (newExpression, newNameElement) = OperatorToFunctionConverter.convert(expression)
        if (OperatorNameConventions.INVOKE.asString() == newName && newExpression is KtDotQualifiedExpression) {
            replaceWithImplicitInvokeInvocation(newExpression)?.let { return it }
        }

        newNameElement.mainReference.handleElementRename(newName)
        return newExpression
    }
}