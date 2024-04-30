// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler
import com.intellij.refactoring.move.moveMembers.MoveMemberHandler
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

/**
 * A usage from the K2 move refactoring.
 */
sealed class K2MoveRenameUsageInfo(
    element: PsiElement,
    reference: PsiReference,
    referencedElement: PsiNamedElement
) : MoveRenameUsageInfo(element, reference, referencedElement) {
    protected fun PsiNamedElement.correctTarget() = when {
        this is KtConstructor<*> -> containingClass() ?: error("Constructor had no containing class")
        this is PsiMethod && isConstructor -> containingClass ?: error("Constructor had no containing class")
        else -> this
    }

    abstract fun retarget(to: PsiNamedElement): PsiElement?

    /**
     * A usage described in a foreign language like Java or Groovy.
     */
    class Light(
        element: PsiElement,
        reference: PsiReference,
        referencedElement: KtNamedDeclaration,
        private val wasMember: Boolean,
        private val oldContainingFqn: String?,
        private val lightElementIndex: Int,
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        override fun retarget(to: PsiNamedElement): PsiElement? {
            if (to !is KtNamedDeclaration) error("Usage must reference a Kotlin element")
            val element = element ?: return element
            val newLightElement = to.toLightElements()[lightElementIndex]
            if (element is PsiReferenceExpression
                && wasMember
                && newLightElement is PsiMember
                && updateJavaReference(element, oldContainingFqn, newLightElement)
            ) return element
            return reference?.bindToElement(newLightElement)
        }

        private fun updateJavaReference(
            reference: PsiReferenceExpression,
            oldClassFqn: String?,
            newElement: PsiMember
        ): Boolean {
            // TODO do proper implementation here where it keeps the import as-is
            val importOfOldClass = (reference.containingFile as? PsiJavaFile)?.importList?.allImportStatements?.firstOrNull {
                when (it) {
                    is PsiImportStatement -> it.qualifiedName == oldClassFqn
                    is PsiImportStaticStatement -> it.isOnDemand && it.importReference?.canonicalText == oldClassFqn
                    else -> false
                }
            }
            if (importOfOldClass != null && importOfOldClass.resolve() == null) {
                importOfOldClass.delete()
            }

            val newClass = newElement.containingClass
            if (newClass != null && reference.qualifierExpression != null) {
                val options = object : MoveMembersOptions {
                    override fun getMemberVisibility(): String = PsiModifier.PUBLIC
                    override fun makeEnumConstant(): Boolean = true
                    override fun getSelectedMembers(): Array<PsiMember> = arrayOf(newElement)
                    override fun getTargetClassName(): String? = newClass.qualifiedName
                }
                val usageInfo = MoveMembersProcessor.MoveMembersUsageInfo(
                    newElement, reference.element, newClass, reference.qualifierExpression, reference
                )
                val moveMemberHandler = MoveMemberHandler.EP_NAME.forLanguage(reference.element.language)
                if (moveMemberHandler != null) {
                    moveMemberHandler.changeExternalUsage(options, usageInfo)
                    return true
                }
            }
            return false
        }
    }

    /**
     * A Kotlin usage, Kotlin usages can either be internal and external.
     * Internal usages are usages that are moved, external usages are usages that reference a moved declaration.
     * A reference can be both an internal and external usage; this would mean that both the reference and the referenced declarations are
     * moved in this move operation.
     */
    class Source(
        element: KtElement,
        reference: KtSimpleNameReference,
        referencedElement: PsiNamedElement,
        val isInternal: Boolean
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        fun refresh(referenceElement: PsiElement, referencedElement: PsiNamedElement): K2MoveRenameUsageInfo {
            if (referenceElement !is KtElement) return this
            val reference = (referenceElement.mainReference as? KtSimpleNameReference) ?: return this
            return Source(referenceElement, reference, referencedElement, isInternal)
        }

        override fun retarget(to: PsiNamedElement): PsiElement? {
            val reference = element?.reference as? KtSimpleNameReference ?: return null
            val target = to.correctTarget()
            return reference.bindToElement(target, KtSimpleNameReference.ShorteningMode.NO_SHORTENING)
        }
    }

    companion object {
        fun find(declaration: KtNamedDeclaration): List<UsageInfo> {
            markInternalUsages(declaration)
            return preProcessUsages(declaration.collectDescendantsOfType<KtNamedDeclaration>().flatMap { findExternalUsages(it) })
        }

        /**
         * Removes unwanted usages, like, for example, usages through import aliases.
         */
        private fun preProcessUsages(usages: List<UsageInfo>): List<UsageInfo> {
            MoveClassHandler.EP_NAME.extensionList.forEach { handler -> handler.preprocessUsages(usages) }
            return usages
        }

        /**
         * Used to store usage info for an internal reference so that after the move the referenced can be restored.
         * @see restoreInternalUsages
         * @see K2MoveRenameUsageInfo.Source.refresh
         */
        internal var KtSimpleNameExpression.internalUsageInfo: K2MoveRenameUsageInfo? by CopyablePsiUserDataProperty(Key.create("INTERNAL_USAGE_INFO"))

        /**
         * Finds any usage inside [containing]. We need these usages because when moving [containing] to a different package references
         * that where previously imported by default might now require an explicit import.
         */
        fun markInternalUsages(containing: KtElement) =
                containing.forEachDescendantOfType<KtSimpleNameExpression> { refExpr ->
                    val resolved = refExpr.mainReference.resolve() as? PsiNamedElement ?: return@forEachDescendantOfType
                    val usageInfo = Source(refExpr, refExpr.mainReference, resolved, true)
                    refExpr.internalUsageInfo = usageInfo
                }

        fun unMarkNonUpdatableUsages(containing: Iterable<PsiElement>) = containing.forEach {
            unMarkNonUpdatableUsages(it)
        }

        /**
         * Removes any internal usage infos that don't need to be updated.
         * In [markInternalUsages] we marked all internal usages, but some of these usages don't need to be updated.
         * Like, for example, instance methods.
         */
        @OptIn(KtAllowAnalysisFromWriteAction::class)
        fun unMarkNonUpdatableUsages(containing: PsiElement) = allowAnalysisFromWriteAction {
            containing.forEachDescendantOfType<KtSimpleNameExpression> { refExpr ->
                if (!refExpr.isImportable()) refExpr.internalUsageInfo = null
            }
        }

        private fun KtSimpleNameExpression.isImportable(): Boolean = analyze(this) {
            val refExpr = this@isImportable
            if (refExpr is KtEnumEntrySuperclassReferenceExpression) return false
            if (refExpr.parent is KtThisExpression || refExpr.parent is KtSuperExpression) return false
            if (refExpr.isUnqualifiable()) return true
            val resolvedSymbol = refExpr.mainReference.resolveToSymbol()
            if (resolvedSymbol is KtConstructorSymbol) return true
            val containingSymbol = resolvedSymbol?.getContainingSymbol()
            if (containingSymbol == null) return true // top levels are static
            if (containingSymbol is KtSymbolWithMembers) {
                val staticScope = containingSymbol.getStaticMemberScope()
                return resolvedSymbol in staticScope.getAllSymbols()
            }
            return false
        }

        /**
         * Finds usages to [declaration] excluding the usages inside [declaration].
         */
        private fun findExternalUsages(declaration: KtNamedDeclaration): List<MoveRenameUsageInfo> {
            return ReferencesSearch.search(declaration, declaration.resolveScope).findAll()
                .filter { !declaration.isAncestor(it.element) } // exclude internal usages
                .mapNotNull { ref ->
                    if (ref is KtSimpleNameReference) {
                        Source(ref.element, ref, declaration, false)
                    } else {
                        val lightElements = declaration.toLightElements()
                        val lightElement = if (lightElements.size == 1) {
                            lightElements.firstOrNull()
                        } else {
                            lightElements.firstOrNull { ref.isReferenceTo(it) }
                        } ?: return@mapNotNull null
                        val fqn = if (lightElement is PsiMember) lightElement.containingClass?.qualifiedName else null
                        Light(ref.element, ref, declaration, lightElement is PsiMember, fqn, lightElements.indexOf(lightElement))
                    }
                }
        }

        private fun KtSimpleNameExpression.isUnqualifiable(): Boolean {
            // example: a.foo() where foo is an extension function
            fun KtSimpleNameExpression.isExtensionReference(): Boolean {
                return analyze(this) {
                    val callable = mainReference.resolveToSymbol() as? KtCallableSymbol
                    if (callable?.isExtension == true) return true
                    if (callable is KtPropertySymbol) {
                        val returnType = callable.returnType
                        returnType is KtFunctionalType && returnType.receiverType != null
                    } else false
                }
            }

            // example: ::foo
            fun KtSimpleNameExpression.isCallableReferenceExpressionWithoutQualifier(): Boolean {
                val parent = parent
                return parent is KtCallableReferenceExpression && parent.receiverExpression == null
            }
            if (parentOfType<KtImportDirective>() != null) return false
            return isExtensionReference() || isCallableReferenceExpressionWithoutQualifier()
        }

        internal fun retargetUsages(usages: List<UsageInfo>, oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>) {
            retargetInternalUsages(oldToNewMap)
            retargetExternalUsages(usages, oldToNewMap)
        }

        /**
         * After moving, internal usages might have become invalid, this method restores these usage infos.
         * @see internalUsageInfo
         */
        private fun restoreInternalUsages(
            containingDecl: KtNamedDeclaration,
            oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>,
            fromCopy: Boolean
        ): List<UsageInfo> {
            return containingDecl.collectDescendantsOfType<KtSimpleNameExpression>().mapNotNull { refExpr ->
                val usageInfo = refExpr.internalUsageInfo
                if (!fromCopy && usageInfo?.element != null) return@mapNotNull usageInfo
                val referencedElement = (usageInfo as? Source)?.referencedElement ?: return@mapNotNull null
                val newReferencedElement = oldToNewMap[referencedElement] ?: referencedElement
                if (!newReferencedElement.isValid || newReferencedElement !is PsiNamedElement) return@mapNotNull null
                usageInfo.refresh(refExpr, newReferencedElement)
            }
        }

        fun retargetInternalUsages(oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>, fromCopy: Boolean = false) {
            val newDeclarations = oldToNewMap.values.toList()
            val internalUsages = newDeclarations
                .flatMap { decl -> restoreInternalUsages(decl, oldToNewMap, fromCopy) }
                .filterIsInstance<K2MoveRenameUsageInfo>()
                .sortedByFile()
            shortenUsages(retargetMoveUsages(internalUsages, oldToNewMap))
        }

        private fun List<K2MoveRenameUsageInfo>.filterUpdatableExternalUsages(movedElements: Set<KtNamedDeclaration>) = filter { usage ->
            usage.referencedElement in movedElements
        }

        private fun retargetExternalUsages(usages: List<UsageInfo>, oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>) {
            val externalUsages = usages
                .filterIsInstance<K2MoveRenameUsageInfo>()
                .filterUpdatableExternalUsages(oldToNewMap.keys.toSet())
                .filter { it.element != null } // if the element is null, it means that this external usage was moved
                .sortedByFile()
            shortenUsages(retargetMoveUsages(externalUsages, oldToNewMap))
        }

        private fun retargetMoveUsages(
            usageInfosByFile: Map<PsiFile, List<K2MoveRenameUsageInfo>>,
            oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>
        ): Map<PsiFile, Map<PsiElement, PsiNamedElement>> {
            return usageInfosByFile.map { (file, usageInfos) ->
                // TODO instead of manually handling of qualifiable/non-qualifiable references we should invoke `bindToElement` in bulk
                file to usageInfos.mapNotNull { usageInfo ->
                    val newDeclaration = (oldToNewMap[usageInfo.referencedElement] ?: usageInfo.referencedElement) as? PsiNamedElement
                        ?: return@mapNotNull null
                    val qualifiedReference = usageInfo.retarget(newDeclaration)
                    if (usageInfo is Source && qualifiedReference != null) {
                        qualifiedReference to newDeclaration
                    } else null
                }.filter { it.first.isValid }.toMap()  // imports can become invalid because they are removed when binding element
            }.toMap()
        }

        private fun shortenUsages(qualifiedUsages: Map<PsiFile, Map<PsiElement, PsiNamedElement>>) {
            qualifiedUsages.forEach { (file, usageMap) ->
                if (file is KtFile) {
                    shortenReferences(usageMap.keys.filterIsInstance<KtElement>())
                }
            }
        }
    }
}