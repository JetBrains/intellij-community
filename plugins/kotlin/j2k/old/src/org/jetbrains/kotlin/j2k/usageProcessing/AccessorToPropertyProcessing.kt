// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.usageProcessing

import com.intellij.psi.*
import org.jetbrains.kotlin.j2k.AccessorKind
import org.jetbrains.kotlin.j2k.CodeConverter
import org.jetbrains.kotlin.j2k.ast.*
import org.jetbrains.kotlin.j2k.dot
import org.jetbrains.kotlin.psi.*

class AccessorToPropertyProcessing(val accessorMethod: PsiMethod, val accessorKind: AccessorKind, val propertyName: String) : UsageProcessing {
    override val targetElement: PsiElement get() = accessorMethod

    override val convertedCodeProcessor = object : ConvertedCodeProcessor {
        override fun convertMethodUsage(methodCall: PsiMethodCallExpression, codeConverter: CodeConverter): Expression? {
            val isNullable = codeConverter.typeConverter.methodNullability(accessorMethod).isNullable(codeConverter.settings)

            val methodExpr = methodCall.methodExpression
            val arguments = methodCall.argumentList.expressions

            val propertyName = Identifier.withNoPrototype(propertyName, isNullable)
            val qualifier = methodExpr.qualifierExpression
            val propertyAccess = if (qualifier != null)
                QualifiedExpression(codeConverter.convertExpression(qualifier), propertyName, methodExpr.dot()).assignNoPrototype()
            else
                propertyName

            if (accessorKind == AccessorKind.GETTER) {
                if (arguments.isNotEmpty()) return null // incorrect call
                return propertyAccess
            }
            else {
                if (arguments.size != 1) return null // incorrect call
                val argument = codeConverter.convertExpression(arguments[0])
                return AssignmentExpression(propertyAccess, argument, Operator.EQ)
            }
        }
    }

    override val javaCodeProcessors = emptyList<ExternalCodeProcessor>()

    override val kotlinCodeProcessors =
            if (accessorMethod.hasModifierProperty(PsiModifier.PRIVATE))
                emptyList()
            else
                listOf(AccessorToPropertyProcessor(propertyName, accessorKind))

    class AccessorToPropertyProcessor(
        private val propertyName: String,
        private val accessorKind: AccessorKind
    ) : ExternalCodeProcessor {
        override fun processUsage(reference: PsiReference): Array<PsiReference>? {
            return processUsage(reference.element, propertyName, accessorKind)
        }
    }

    companion object {
        fun processUsage(element: PsiElement, propertyName: String, accessorKind: AccessorKind): Array<PsiReference>? {
            val nameExpr = element as? KtSimpleNameExpression ?: return null
            val callExpr = nameExpr.parent as? KtCallExpression ?: return null

            val arguments = callExpr.valueArguments

            val factory = KtPsiFactory(nameExpr.project)
            var propertyNameExpr = factory.createSimpleName(propertyName)
            if (accessorKind == AccessorKind.GETTER) {
                if (arguments.size != 0) return null // incorrect call
                propertyNameExpr = callExpr.replace(propertyNameExpr) as KtSimpleNameExpression
                return propertyNameExpr.references
            } else {
                val value = arguments.singleOrNull()?.getArgumentExpression() ?: return null
                var assignment = factory.createExpression("a = b") as KtBinaryExpression
                assignment.right!!.replace(value)

                val qualifiedExpression = callExpr.parent as? KtQualifiedExpression
                return if (qualifiedExpression != null && qualifiedExpression.selectorExpression == callExpr) {
                    callExpr.replace(propertyNameExpr)
                    assignment.left!!.replace(qualifiedExpression)
                    assignment = qualifiedExpression.replace(assignment) as KtBinaryExpression
                    (assignment.left as KtQualifiedExpression).selectorExpression!!.references
                } else {
                    assignment.left!!.replace(propertyNameExpr)
                    assignment = callExpr.replace(assignment) as KtBinaryExpression
                    assignment.left!!.references
                }
            }
        }
    }
}
