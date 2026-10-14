// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inlineSuperClass

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionCall
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

internal fun companionUnsupportedReason(
    source: KtClass,
    companion: KtObjectDeclaration,
    targets: List<PsiElement>,
): @NlsContexts.DialogMessage String? {
    if (targets.size != 1) return KotlinBundle.message("inline.super.class.companion.multiple.inheritors")
    val target = targets.single() as? KtClassOrObject
    if (target == null || target is KtClass && target.isInner()) {
        return KotlinBundle.message("inline.super.class.companion.unsupported.target")
    }
    val targetCompanion = target.companionObjects.singleOrNull() ?: return null
    if (companion.superTypeListEntries.isNotEmpty() || targetCompanion.superTypeListEntries.isNotEmpty()) {
        return KotlinBundle.message("inline.super.class.companion.merge.supertype")
    }
    val targetNames = targetCompanion.declarations.filterIsInstance<KtNamedDeclaration>().mapNotNull { it.name }.toSet()
    for (member in companion.declarations.filterIsInstance<KtNamedDeclaration>()) {
        val name = member.name ?: continue
        if (name in targetNames) return KotlinBundle.message("inline.super.class.companion.member.clash", name)
    }
    for (objectDeclaration in listOf(companion, targetCompanion)) {
        if (objectDeclaration.annotationEntries.isNotEmpty() || objectDeclaration.declarations.any {
                it !is KtNamedFunction && !(it is KtProperty && it.hasModifier(KtTokens.CONST_KEYWORD))
            }) {
            return KotlinBundle.message("inline.super.class.companion.merge.initialization")
        }
        if (objectDeclaration.collectDescendantsOfType<KtThisExpression>().any {
                it.instanceReference.mainReference.resolve() == objectDeclaration
            } || hasIdentitySensitiveCalls(objectDeclaration)) {
            return KotlinBundle.message("inline.super.class.companion.merge.identity")
        }
    }
    for (declaration in listOf(source, target, companion, targetCompanion)) {
        if (ReferencesSearch.search(declaration).any { reference ->
                val element = reference.element
                element.getStrictParentOfType<KtImportDirective>() == null &&
                        !isCompanionMemberAccess(element, source, target) &&
                        !(declaration is KtClass && isClassUsage(element))
            }) {
            return KotlinBundle.message("inline.super.class.companion.merge.identity")
        }
    }
    return null
}

private fun hasIdentitySensitiveCalls(companion: KtObjectDeclaration): Boolean = analyze(companion) {
    companion.collectDescendantsOfType<KtSimpleNameExpression>().any { expression ->
        val call = expression.resolveSuccessfulExpressionCall()?.simple ?: return@any false
        val receiver = call.extensionReceiver ?: call.dispatchReceiver ?: return@any false
        receiver.type.expandedSymbol?.psi == companion &&
                expression.resolveSuccessfulExpressionSymbol()?.psi?.let { !PsiTreeUtil.isAncestor(companion, it, false) } != false
    }
}

private fun isCompanionMemberAccess(element: PsiElement, source: KtClass, target: KtClassOrObject): Boolean {
    val parent = element.parent
    val expression = if (parent is KtQualifiedExpression && parent.selectorExpression == element) parent else element
    val qualified = expression.parent as? KtQualifiedExpression ?: return false
    if (qualified.receiverExpression != expression) return false
    val selector = qualified.selectorExpression?.getCalleeExpressionIfAny() ?: return false
    val declaration = analyze(selector) { selector.resolveSuccessfulExpressionSymbol()?.psi } ?: return false
    return PsiTreeUtil.isAncestor(source, declaration, false) || PsiTreeUtil.isAncestor(target, declaration, false)
}

private fun isClassUsage(element: PsiElement): Boolean {
    val parent = element.parent
    return parent is KtUserType || parent is KtClassLiteralExpression ||
            parent is KtCallExpression && parent.calleeExpression == element ||
            element.getStrictParentOfType<KtSuperTypeListEntry>() != null
}
