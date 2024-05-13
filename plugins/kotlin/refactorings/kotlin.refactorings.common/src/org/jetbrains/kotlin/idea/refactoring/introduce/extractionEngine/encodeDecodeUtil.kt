// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.utils.isImplicitInvokeCall
import org.jetbrains.kotlin.psi.CopyablePsiUserDataProperty
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getContentRange
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

data class ResolveResult<Descriptor, ResolvedCall>(
    val originalRefExpr: KtReferenceExpression,
    val declaration: PsiElement,
    val descriptor: Descriptor,
    val resolvedCall: ResolvedCall?
)

data class ResolvedReferenceInfo<Descriptor, ResolvedCall, KotlinType>(
    val refExpr: KtReferenceExpression,
    val resolveResult: ResolveResult<Descriptor, ResolvedCall>,
    val smartCast: KotlinType?,
    val possibleTypes: Set<KotlinType>
)

var KtReferenceExpression.resolveResult: ResolveResult<*, *>? by CopyablePsiUserDataProperty(Key.create("RESOLVE_RESULT"))

fun unmarkReferencesInside(root: PsiElement) {
    runReadAction {
        if (!root.isValid) return@runReadAction
        root.forEachDescendantOfType<KtReferenceExpression> { it.resolveResult = null }
    }
}

fun <Descriptor, ResolvedCall> IExtractionData.encodeReferences(
    processImplicitInvoke: Boolean,
    hasSmartCast: (KtQualifiedExpression) -> Boolean,
    resolveResultProvider: (KtReferenceExpression) -> ResolveResult<Descriptor, ResolvedCall>?
) {
    val visitor = object : KtTreeVisitorVoid() {
        override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
            if (hasSmartCast(expression)) {
                expression.selectorExpression?.accept(this)
                return
            }

            super.visitQualifiedExpression(expression)
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            if (processImplicitInvoke) {
                val implicitInvoke =
                    analyze(expression) { expression.isImplicitInvokeCall() }
                if (implicitInvoke == true) {
                    expression.resolveResult = resolveResultProvider(expression)
                }
            }
            super.visitCallExpression(expression)
        }

        override fun visitSimpleNameExpression(ref: KtSimpleNameExpression) {
            if (ref.parent is KtValueArgumentName) return

            val physicalRef = substringInfo?.let {
                // If substring contains some references it must be extracted as a string template
                val physicalExpression = expressions.single() as KtStringTemplateExpression
                val extractedContentOffset = physicalExpression.getContentRange().startOffset + physicalExpression.startOffset
                val offsetInExtracted = ref.startOffset - extractedContentOffset
                val offsetInTemplate = it.relativeContentRange.startOffset + offsetInExtracted
                it.template.findElementAt(offsetInTemplate)!!.getStrictParentOfType<KtSimpleNameExpression>()
            } ?: ref

            val resolveResult = resolveResultProvider(physicalRef) ?: return
            physicalRef.resolveResult = resolveResult
            if (ref != physicalRef) {
                ref.resolveResult = resolveResult
            }
        }
    }
    expressions.forEach { it.accept(visitor) }
}