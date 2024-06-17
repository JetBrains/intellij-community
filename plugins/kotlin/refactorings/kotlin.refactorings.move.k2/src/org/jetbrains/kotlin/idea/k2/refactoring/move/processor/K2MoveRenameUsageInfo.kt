// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler
import com.intellij.refactoring.move.moveMembers.MoveMemberHandler
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithMembers
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.canBeUsedInImport
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.refactoring.nameDeterminant
import org.jetbrains.kotlin.idea.references.KtConstructorDelegationReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

/**
 * A usage from the K2 move refactoring. Not all usages need to be updated, some are only used for conflict checking.
 * Moving, for example, an instance method can result in the method losing visibility to a usage, but just this method doesn't need updating.
 */
sealed class K2MoveRenameUsageInfo(
    element: PsiElement,
    reference: PsiReference,
    referencedElement: PsiNamedElement
) : MoveRenameUsageInfo(element, reference, referencedElement) {
    /**
     * Returns whether this usage info is actually required for updating.
     * Sometimes it can depend on the language of the usage whether the usage info is updatable.
     * In Kotlin, for example, object members can be imported and might require updating, but in Java these are regular instance methods and
     * references to these methods can't be updated.
     */
    abstract fun isUpdatable(movedElements: List<KtNamedDeclaration>): Boolean

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
        override fun isUpdatable(movedElements: List<KtNamedDeclaration>): Boolean {
            return true // TODO write better updatable check for light references
        }

        override fun retarget(to: PsiNamedElement): PsiElement? {
            if (to !is KtNamedDeclaration) error("Usage must reference a Kotlin element")
            val element = element ?: return element
            val newLightElement = to.toLightElements()[lightElementIndex]
            if (element.reference?.isReferenceTo(newLightElement) == true) return element
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
        element: KtReferenceExpression,
        reference: KtReference,
        referencedElement: PsiNamedElement,
        val isInternal: Boolean
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        @OptIn(KaAllowAnalysisOnEdt::class)
        override fun isUpdatable(movedElements: List<KtNamedDeclaration>): Boolean = allowAnalysisOnEdt {
            val refExpr = element as KtSimpleNameExpression
            if (!refExpr.canBeUsedInImport()) return false
            if (refExpr.parentOfType<KtImportDirective>(withSelf = false) != null) return true
            if (refExpr.isUnqualifiable()) return true
            val refChain = (refExpr.getTopmostParentQualifiedExpressionForReceiver() ?: refExpr)
                .collectDescendantsOfType<KtSimpleNameExpression>()
                .filter { it.canBeUsedInImport() }
            return if (isInternal) {
                // for internal usages, update the first name determinant in the call chain
                refChain.firstOrNull { simpleNameExpr -> simpleNameExpr.isNameDeterminantInQualifiedChain() } == refExpr
            } else {
                // for external usages, update the first reference to a moved element
                refChain.firstOrNull { simpleNameExpr -> simpleNameExpr.mainReference.resolve() in movedElements } == refExpr
            }
        }

        private fun KtSimpleNameExpression.getTopmostParentQualifiedExpressionForReceiver(): KtExpression? {
            return generateSequence<KtExpression>(this) {
                it.parent as? KtQualifiedExpression ?: it.parent as? KtCallExpression
            }.lastOrNull()
        }

        @OptIn(KaAllowAnalysisOnEdt::class)
        private fun KtSimpleNameExpression.isNameDeterminantInQualifiedChain(): Boolean = allowAnalysisOnEdt {
            analyze(this) {
                val resolvedSymbol = mainReference.resolveToSymbol()
                if (resolvedSymbol is KaClassOrObjectSymbol && resolvedSymbol.classKind == KaClassKind.COMPANION_OBJECT) return true
                if (resolvedSymbol is KaConstructorSymbol) return true
                val containingSymbol = resolvedSymbol?.getContainingSymbol()
                if (resolvedSymbol is KaPackageSymbol) return false // ignore packages
                if (containingSymbol == null) return true // top levels are static
                if (containingSymbol is KaClassOrObjectSymbol) {
                    when (containingSymbol.classKind) {
                        KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT, KaClassKind.ENUM_CLASS -> return true
                        else -> {}
                    }
                }
                if (containingSymbol is KaSymbolWithMembers) {
                    if (resolvedSymbol in containingSymbol.getStaticMemberScope().getAllSymbols()) return true
                }
                return false
            }
        }

        private fun KtSimpleNameExpression.isUnqualifiable(): Boolean {
            // example: a.foo() where foo is an extension function
            fun KtSimpleNameExpression.isExtensionReference(): Boolean {
                return analyze(this) {
                    val callable = mainReference.resolveToSymbol() as? KaCallableSymbol
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
            return isExtensionReference() || isCallableReferenceExpressionWithoutQualifier()
        }

        fun refresh(refExpr: KtReferenceExpression, referencedElement: PsiNamedElement): K2MoveRenameUsageInfo {
            val reference = (refExpr.mainReference as? KtReference) ?: return this
            return Source(refExpr, reference, referencedElement, isInternal)
        }

        override fun retarget(to: PsiNamedElement): PsiElement? {
            val reference = element?.reference as? KtReference ?: return null
            if (reference is KtSimpleNameReference) {
                // shortening will be done later when all references are updated and the code isn't broken anymore
                return reference.bindToElement(to, KtSimpleNameReference.ShorteningMode.NO_SHORTENING)
            } else {
                return reference.bindToElement(to)
            }
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
        internal var KtReferenceExpression.internalUsageInfo: K2MoveRenameUsageInfo? by CopyablePsiUserDataProperty(Key.create("INTERNAL_USAGE_INFO"))

        /**
         * Finds any usage inside [containing]. We need these usages because when moving [containing] to a different package references
         * that where previously imported by default might now require an explicit import.
         */
        fun markInternalUsages(containing: KtElement) {
            containing.forEachDescendantOfType<KtReferenceExpression> { refExpr ->
                if (refExpr is KtEnumEntrySuperclassReferenceExpression) return@forEachDescendantOfType
                val mainReference= refExpr.mainReference
                if (mainReference is KtConstructorDelegationReference) return@forEachDescendantOfType
                val resolved = mainReference.resolve() as? PsiNamedElement ?: return@forEachDescendantOfType
                refExpr.internalUsageInfo = Source(refExpr, mainReference, resolved, true)
            }
        }

        fun unMarkAllUsages(containing: KtElement) = containing.forEachDescendantOfType<KtSimpleNameExpression> { refExpr ->
            refExpr.internalUsageInfo = null
        }


        /**
         * Removes any internal usage infos that don't need to be updated.
         * In [markInternalUsages] we marked all internal usages, but some of these usages don't need to be updated.
         * Like, for example, instance methods.
         */
        fun unMarkNonUpdatableUsages(movedElements: List<KtNamedDeclaration>) {
            for (declaration in movedElements) {
                declaration.forEachDescendantOfType<KtSimpleNameExpression> { refExpr ->
                    val usageInfo = refExpr.internalUsageInfo ?: return@forEachDescendantOfType
                    if (!usageInfo.isUpdatable(movedElements)) refExpr.internalUsageInfo = null
                }
            }
        }

        /**
         * Finds usages to [declaration] excluding the usages inside [declaration].
         */
        private fun findExternalUsages(declaration: KtNamedDeclaration): List<MoveRenameUsageInfo> {
            return ReferencesSearch.search(declaration, declaration.useScope).findAll()
                .filter { !declaration.isAncestor(it.element) } // exclude internal usages
                .mapNotNull { ref ->
                    if (ref is KtSimpleNameReference) {
                        Source(ref.element, ref, declaration.nameDeterminant(), false)
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

        internal fun retargetUsages(usages: List<K2MoveRenameUsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>) {
            retargetInternalUsages(oldToNewMap)
            retargetExternalUsages(usages, oldToNewMap)
        }

        /**
         * After moving, internal usages might have become invalid, this method restores these usage infos.
         * @see internalUsageInfo
         */
        private fun restoreInternalUsages(
            containingDecl: KtNamedDeclaration,
            oldToNewMap: Map<PsiElement, PsiElement>,
            fromCopy: Boolean
        ): List<UsageInfo> {
            return containingDecl.collectDescendantsOfType<KtReferenceExpression>().mapNotNull { refExpr ->
                val usageInfo = refExpr.internalUsageInfo
                if (!fromCopy && usageInfo?.element != null) return@mapNotNull usageInfo
                val referencedElement = (usageInfo as? Source)?.referencedElement ?: return@mapNotNull null
                val newReferencedElement = oldToNewMap[referencedElement] ?: referencedElement
                if (!newReferencedElement.isValid || newReferencedElement !is PsiNamedElement) return@mapNotNull null
                usageInfo.refresh(refExpr, newReferencedElement)
            }
        }

        /**
         * If file copy was done through vfs, then copyable user data is not preserved.
         * For this case, let's rely on the fact that in the same write action,
         * copy and original files are identical and retrieve original resolve targets from initial user data.
         */
        fun retargetInternalUsagesForCopyFile(
            originalFile: KtFile,
            fileCopy: KtFile,
        ) {
            val inCopy = fileCopy.collectDescendantsOfType<KtSimpleNameExpression>()
            val original = originalFile.collectDescendantsOfType<KtSimpleNameExpression>()
            val internalUsages = original.zip(inCopy).mapNotNull { (o, c) ->
                if (PsiTreeUtil.getParentOfType(o, KtPackageDirective::class.java) != null) return@mapNotNull null
                val usageInfo = o.internalUsageInfo
                val referencedElement = (usageInfo as? Source)?.referencedElement ?: return@mapNotNull null
                if (!referencedElement.isValid ||
                    referencedElement !is PsiNamedElement ||
                    PsiTreeUtil.isAncestor(originalFile, referencedElement, true)
                ) {
                    return@mapNotNull null
                }
                usageInfo.refresh(c, referencedElement)
            }

            shortenUsages(retargetMoveUsages(mapOf(fileCopy to internalUsages), emptyMap()))
        }

        fun retargetInternalUsages(oldToNewMap: Map<PsiElement, PsiElement>, fromCopy: Boolean = false) {
            val newDeclarations = oldToNewMap.values.toList().filterIsInstance<KtNamedDeclaration>()
            val internalUsages = newDeclarations
                .flatMap { decl -> restoreInternalUsages(decl, oldToNewMap, fromCopy) }
                .filterIsInstance<K2MoveRenameUsageInfo>()
                .groupByFile()
                .sortedByOffset()
            shortenUsages(retargetMoveUsages(internalUsages, oldToNewMap))
        }

        private fun retargetExternalUsages(usages: List<K2MoveRenameUsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>) {
            val externalUsages = usages
                .filter { it.element != null } // if the element is null, it means that this external usage was moved
                .groupByFile()
                .sortedByOffset()
            shortenUsages(retargetMoveUsages(externalUsages, oldToNewMap))
        }

        private fun retargetMoveUsages(
            usageInfosByFile: Map<PsiFile, List<K2MoveRenameUsageInfo>>,
            oldToNewMap: Map<PsiElement, PsiElement>
        ): Map<PsiFile, Map<PsiElement, PsiNamedElement>> {
            return usageInfosByFile.map { (file, usageInfos) ->
                file to usageInfos.mapNotNull { usageInfo ->
                    val newDeclaration = (oldToNewMap[usageInfo.referencedElement] ?: usageInfo.referencedElement) as? PsiNamedElement
                        ?: return@mapNotNull null
                    val retargetedReference = usageInfo.retarget(newDeclaration)
                    val qualifiedReference = if (retargetedReference is KtSimpleNameExpression) {
                        // get top most qualified for shortening if we don't have it already
                        generateSequence<KtElement>(retargetedReference) {
                            it.parent as? KtQualifiedExpression ?: it.parent as? KtCallExpression ?: it.parent as? KtUserType
                        }.last()
                    } else retargetedReference
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