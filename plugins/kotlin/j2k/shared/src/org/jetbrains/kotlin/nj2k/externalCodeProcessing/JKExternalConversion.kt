// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.externalCodeProcessing

import com.intellij.psi.*
import org.jetbrains.kotlin.j2k.AccessorKind
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.cast

sealed class JKExternalConversion : Comparable<JKExternalConversion> {
    abstract val usage: PsiElement

    abstract fun apply()

    private val depth by lazy(LazyThreadSafetyMode.NONE) { usage.parentsWithSelf.takeWhile { it !is PsiFile }.count() }
    private val offset by lazy(LazyThreadSafetyMode.NONE) { usage.textRange.startOffset }

    override fun compareTo(other: JKExternalConversion): Int {
        val depth1 = depth
        val depth2 = other.depth
        if (depth1 != depth2) { // put deeper elements first to not invalidate them when processing ancestors
            return -depth1.compareTo(depth2)
        }

        // process elements with the same deepness from right to left
        // so that right-side of assignments is not invalidated by processing of the left one
        return -offset.compareTo(other.offset)
    }
}

class AccessorToPropertyKotlinExternalConversion(
    private val name: String,
    private val accessorKind: AccessorKind,
    override val usage: PsiElement
) : JKExternalConversion() {
    override fun apply() {
        val nameExpr = usage as? KtSimpleNameExpression ?: return
        val callExpr = nameExpr.parent as? KtCallExpression ?: return

        val arguments = callExpr.valueArguments
        val factory = KtPsiFactory(nameExpr.project)
        val propertyNameExpr = factory.createSimpleName(name)

        if (accessorKind == AccessorKind.GETTER) {
            if (arguments.size != 0) return // incorrect call
            callExpr.replace(propertyNameExpr)
            return
        }

        val value = arguments.singleOrNull()?.getArgumentExpression() ?: return
        val assignment = factory.createExpression("a = b") as KtBinaryExpression
        assignment.right!!.replace(value)
        val qualifiedExpression = callExpr.parent as? KtQualifiedExpression

        if (qualifiedExpression != null && qualifiedExpression.selectorExpression == callExpr) {
            callExpr.replace(propertyNameExpr)
            assignment.left!!.replace(qualifiedExpression)
            qualifiedExpression.replace(assignment)
        } else {
            assignment.left!!.replace(propertyNameExpr)
            callExpr.replace(assignment)
        }
    }
}

class AccessorToPropertyJavaExternalConversion(
    private val name: String,
    private val accessorKind: AccessorKind,
    override val usage: PsiElement
) : JKExternalConversion() {
    override fun apply() {
        if (usage !is PsiReferenceExpression) return
        val methodCall = usage.parent as? PsiMethodCallExpression ?: return

        val factory = PsiElementFactory.getInstance(usage.project)
        val propertyAccess = factory.createReferenceExpression(usage.qualifierExpression)
        val newExpression = when (accessorKind) {
            AccessorKind.GETTER -> propertyAccess
            AccessorKind.SETTER -> {
                val value = methodCall.argumentList.expressions.singleOrNull() ?: return
                factory.createAssignment(propertyAccess, value)
            }
        }
        methodCall.replace(newExpression)
    }

    private fun PsiElementFactory.createReferenceExpression(qualifier: PsiExpression?): PsiReferenceExpression =
        createExpressionFromText(qualifier?.let { "qualifier." }.orEmpty() + name, usage).cast<PsiReferenceExpression>().apply {
            qualifierExpression?.replace(qualifier ?: return@apply)
        }

    private fun PsiElementFactory.createAssignment(target: PsiExpression, value: PsiExpression): PsiAssignmentExpression =
        createExpressionFromText("x = 1", usage).cast<PsiAssignmentExpression>().apply {
            lExpression.replace(target)
            rExpression!!.replace(value)
        }
}

class PropertyRenamedKotlinExternalUsageConversion(
    private val newName: String,
    override val usage: KtElement
) : JKExternalConversion() {
    override fun apply() {
        if (usage !is KtSimpleNameExpression) return
        val psiFactory = KtPsiFactory(usage.project)
        usage.getReferencedNameElement().replace(psiFactory.createExpression(newName))
    }
}

class PropertyRenamedJavaExternalUsageConversion(
    private val newName: String,
    override val usage: PsiElement
) : JKExternalConversion() {
    override fun apply() {
        if (usage !is PsiReferenceExpression) return
        val factory = PsiElementFactory.getInstance(usage.project)
        usage.referenceNameElement?.replace(factory.createExpressionFromText(newName, usage))
    }
}
