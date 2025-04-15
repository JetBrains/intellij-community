// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages

import com.intellij.openapi.progress.ProgressManager
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
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.analysis.canAddRootPrefix
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.groupByFile
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.sortedByOffset
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.addRootPrefix
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.restoreInternalUsages
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.updatableUsageInfo
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE

/**
 * A usage from the K2 move refactoring. Not all usages need to be updated, some are only used for conflict checking.
 * Moving, for example, an instance method can result in the method losing visibility to a usage, but just this method doesn't need updating.
 */
sealed class K2MoveRenameUsageInfo(
  element: PsiElement,
  reference: PsiReference,
  referencedElement: PsiNamedElement
) : MoveRenameUsageInfo(element, reference, referencedElement) {
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
            return try {
                 reference?.bindToElement(newLightElement)
            } catch (_: IncorrectOperationException) {
                // This is thrown if the bind cannot be handled for some reason, in which case we should ignore this exception.
                // An example is the embedded query language in Spring that references Spring entities, but
                // we cannot rebind the references inside this query language.
                element
            }
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
      reference: KtReference,
      referencedElement: PsiNamedElement,
      val isInternal: Boolean
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        fun refresh(element: KtElement, referencedElement: PsiNamedElement): K2MoveRenameUsageInfo {
            val reference = element.mainReference ?: return this
            return Source(element, reference, referencedElement, isInternal)
        }

        override fun retarget(to: PsiNamedElement): PsiElement? {
            // Cannot qualify labels
            if (element is KtLabelReferenceExpression) return null
            val reference = element?.reference as? KtReference ?: return null
            val qualifiedElement = if (reference is KtSimpleNameReference) {
                // shortening will be done later when all references are updated and the code isn't broken anymore
                val boundElement = reference.bindToElement(to, KtSimpleNameReference.ShorteningMode.NO_SHORTENING)
                // The fully qualified bound element might now capture local variables, at which point the semantics would be altered as
                // the element will not refer to the correct symbol anymore.
                // To avoid this, we add the special reserved root package prefix to qualified names.
                // The prefix will be removed later after shortening has taken place.
                if (boundElement is KtDotQualifiedExpression && boundElement.canAddRootPrefix()) {
                    val psiFactory = KtPsiFactory(boundElement.project)
                    boundElement.addRootPrefix(psiFactory)
                    boundElement
                } else boundElement
            } else {
                reference.bindToElement(to)
            }
            return qualifiedElement
        }
    }

    companion object {
        fun find(declaration: KtNamedDeclaration): List<UsageInfo> {
            markInternalUsages(declaration, declaration)
            return findExternalUsages(declaration)
        }

        /**
         * Removes unwanted usages, like, for example, usages through import aliases.
         */
        private fun preProcessUsages(usages: List<K2MoveRenameUsageInfo>): List<K2MoveRenameUsageInfo> {
            MoveClassHandler.EP_NAME.extensionList.forEach { handler -> handler.preprocessUsages(usages) }
            return usages.filter { it.element !is KtPropertyDelegate } // for property delegates, process simple name reference instead
        }

        /**
         * Used to store usage info for an internal reference so that after the move the referenced can be restored.
         * @see restoreInternalUsages
         * @see K2MoveRenameUsageInfo.Source.refresh
         */
        internal val KtElement.internalUsageInfo get() = updatableUsageInfo ?: nonUpdatableUsageInfo

        /**
         * Internal usage info that can be retargeted.
         */
        internal var KtElement.updatableUsageInfo: K2MoveRenameUsageInfo? by CopyablePsiUserDataProperty(
          Key.create("UPDATABLE_INTERNAL_USAGE_INFO"))

        /**
         * Internal usage info that can't be retargeted but might still be useful in, for example, conflict checking.
         */
        internal var KtElement.nonUpdatableUsageInfo: K2MoveRenameUsageInfo? by CopyablePsiUserDataProperty(
          Key.create("NON_UPDATABLE_INTERNAL_USAGE_INFO"))

        fun PsiElement.internalUsageElements() = collectDescendantsOfType<KDocName>() +
                                                 collectDescendantsOfType<KtReferenceExpression>() +
                                                 collectDescendantsOfType<KtForExpression>()

        /**
         * Finds any usage inside [containing].
         * We need these usages because when moving [containing] to a different package references that where previously imported by default might
         * now require an explicit import.
         * @see com.intellij.codeInsight.ChangeContextUtil.encodeContextInfo for Java implementation
         */
        fun markInternalUsages(containing: PsiElement, topLevelMoved: KtElement) {
            containing.internalUsageElements().forEach { refElem ->
                when (refElem) {
                    is KDocName -> {
                        val reference = refElem.mainReference
                        val resolved = reference.resolve() as? PsiNamedElement
                        if (resolved != null && !PsiTreeUtil.isAncestor(topLevelMoved, resolved, false)) {
                            refElem.updatableUsageInfo = Source(refElem, reference, resolved, true)
                        }
                    }

                    is KtReferenceExpression -> {
                        refElem.markInternalUsageInfo(topLevelMoved)
                    }

                    is KtForExpression -> {
                        val mainReference = refElem.mainReference
                        if (mainReference != null) {
                            val declPsi = analyze(refElem) {
                              mainReference.resolveToSymbols().firstOrNull { declSymbol ->
                                (declSymbol as KaCallableSymbol).isExtensionDecl()
                              }?.psi
                            }
                            if (declPsi is PsiNamedElement) {
                                refElem.updatableUsageInfo = Source(refElem, mainReference, declPsi, true)
                            }
                        }
                    }
                }
            }
        }

        private fun KtReferenceExpression.markInternalUsageInfo(topLevelMoved: KtElement) {
            val expr = this
            val mainReference = expr.mainReference
            if (expr is KtCallExpression && mainReference !is KtInvokeFunctionReference) return // to avoid duplication when handling name
            if (expr is KtEnumEntrySuperclassReferenceExpression) return
            if (expr is KtCollectionLiteralExpression) return
            val parent = expr.parent
            if (parent is KtSuperExpression || parent is KtThisExpression || parent is KtValueArgumentName) return
            if (expr.parentOfType<KtPackageDirective>() != null) return
            if (expr.parentOfType<KtImportDirective>(withSelf = false) != null) return
            if (mainReference is KtConstructorDelegationReference) return
            val resolved = mainReference.resolve() as? PsiNamedElement ?: return
            val isExtensionReference = if (resolved is KtCallableDeclaration) {
              analyze(resolved) {
                val symbol = resolved.symbol
                if (symbol is KaCallableSymbol) symbol.isExtensionDecl() else false
              }
            } else false
            if (resolved is KtParameter) return
            if (resolved is KtNamedDeclaration && resolved.isDeclaredInContainingContext(expr, topLevelMoved)) return
            val usageInfo = Source(expr, mainReference, resolved, true)
            if (expr.isFirstReferenceInQualifiedChain() || isExtensionReference) {
                expr.updatableUsageInfo = usageInfo
            } else {
                expr.nonUpdatableUsageInfo = usageInfo
            }
        }

        private fun KtNamedDeclaration.isDeclaredInContainingContext(expr: KtReferenceExpression, topLevelMoved: KtElement): Boolean {
            return generateSequence<KtNamedDeclaration>(expr.parentOfType<KtNamedDeclaration>()) { containing ->
                if (containing == topLevelMoved) return@generateSequence null
                containing.parentOfType<KtNamedDeclaration>()
            }.firstOrNull { containing ->
                if (containing is KtDeclarationContainer) {
                    containing.declarations.contains(this)
                } else false
            } != null
        }

        context(KaSession)
        private fun KaCallableSymbol.isExtensionDecl(): Boolean {
            if (isExtension == true) return true
            return if (this is KaPropertySymbol) {
                val returnType = returnType
                returnType is KaFunctionType && returnType.receiverType != null
            } else false
        }

        private fun KtReferenceExpression.isFirstReferenceInQualifiedChain(): Boolean {
            if (this is KtOperationReferenceExpression) return false // don't consider unary expressions like !foo()
            val qualifiedChain =  getQualifiedChainElement().collectDescendantsOfType<KtReferenceExpression>()
            if (qualifiedChain.any { refExpr -> refExpr.updatableUsageInfo != null }) return false // chain is already covered
            for (refExpr in qualifiedChain) {
                val resolved = refExpr.mainReference.resolve()
                if (resolved !is PsiPackage) return this == refExpr
            }
            return false
        }

        private fun KtReferenceExpression.getQualifiedChainElement(): KtElement {
            return generateSequence<KtElement>(this) {
              it.parent as? KtQualifiedExpression ?: it.parent as? KtCallExpression ?: it.parent as? KtUserType
            }.last()
        }

        fun unMarkAllUsages(containing: KtElement) = containing.internalUsageElements().forEach { refExpr ->
            refExpr.updatableUsageInfo = null
            refExpr.nonUpdatableUsageInfo = null
        }

        /**
         *
         * [org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo]
         * After moving, internal usages might have become invalid, this method restores these usage infos.
         * @see updatableUsageInfo
         */
        private fun restoreInternalUsages(
          containing: KtElement,
          oldToNewMap: Map<PsiElement, PsiElement>,
          fromCopy: Boolean
        ): List<UsageInfo> {
            return containing.internalUsageElements().mapNotNull { refExpr ->
                val usageInfo = refExpr.updatableUsageInfo
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
        fun retargetInternalUsagesForCopyFile(originalFile: KtFile, fileCopy: KtFile) {
            val skipPackageStmt: (KtSimpleNameExpression) -> Boolean = { PsiTreeUtil.getParentOfType(it, KtPackageDirective::class.java) == null }
            val inCopy = fileCopy.collectDescendantsOfType<KtSimpleNameExpression>().filter(skipPackageStmt)
            val original = originalFile.collectDescendantsOfType<KtSimpleNameExpression>().filter(skipPackageStmt)
            val internalUsages = original.zip(inCopy).mapNotNull { (o, c) ->
                val usageInfo = o.updatableUsageInfo
                val referencedElement = (usageInfo as? Source)?.referencedElement ?: return@mapNotNull null
                if (!referencedElement.isValid ||
                    referencedElement !is PsiNamedElement ||
                    PsiTreeUtil.isAncestor(originalFile, referencedElement, true)
                ) {
                    return@mapNotNull null
                }
                usageInfo.refresh(c, referencedElement)
            }
            val progress = ProgressManager.getInstance().progressIndicator.apply {
                pushState()
                isIndeterminate = false
            }
            try {
                progress.text = KotlinBundle.message("retargeting.usages.progress")
                val retargetedUsages = retargetMoveUsages(mapOf(fileCopy to internalUsages), emptyMap(), totalFileCount = 1)
                progress.text = KotlinBundle.message("shortening.usages.progress")
                shortenUsages(retargetedUsages)
            } finally {
                progress.popState()
            }
        }

        fun restoreInternalUsagesSorted(
          oldToNewMap: Map<PsiElement, PsiElement>,
          fromCopy: Boolean = false
        ): Map<PsiFile, List<K2MoveRenameUsageInfo>> {
            val newElements = oldToNewMap.values.toList()
            val topLevelElements = newElements
                .filter { elem -> newElements.any { otherElem -> elem.isAncestor(otherElem) } }
                .filterIsInstance<KtElement>()
            return topLevelElements
                .flatMap { decl -> restoreInternalUsages(decl, oldToNewMap, fromCopy) }
                .filterIsInstance<K2MoveRenameUsageInfo>()
                .groupByFile()
                .sortedByOffset()
        }

        /**
         * Finds usages to [declaration] excluding the usages inside [declaration].
         */
        fun findExternalUsages(declaration: KtNamedDeclaration): List<MoveRenameUsageInfo> {
            val allUsages = ReferencesSearch.search(declaration, declaration.project.projectScope()).findAll()
                .filter { !declaration.isAncestor(it.element) } // exclude internal usages
                .mapNotNull { ref ->
                    val element = ref.element
                    if (ref is KtReference && element is KtElement) {
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
            return preProcessUsages(allUsages)
        }

        private fun Map<PsiFile, Map<PsiElement, PsiNamedElement>>.mergedWith(
            other: Map<PsiFile, Map<PsiElement, PsiNamedElement>>
        ): Map<PsiFile, Map<PsiElement, PsiNamedElement>> {
            val result = this.mapValues { it.value.toMutableMap() }.toMutableMap()
            for ((key, value) in other) {
                val collection = result.getOrPut(key) { mutableMapOf() }
                collection.putAll(value)
            }
            return result
        }

        fun retargetUsages(usages: List<K2MoveRenameUsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>, fromCopy: Boolean = false) {
            val externalUsages = externalUsagesSorted(usages)
            val internalUsages = restoreInternalUsagesSorted(oldToNewMap, fromCopy)
            val progress = ProgressManager.getInstance().progressIndicator.apply {
                pushState()
                isIndeterminate = false
            }
            try {
                progress.text = KotlinBundle.message("retargeting.usages.progress")
                // Retarget external usages before internal usages to make sure imports in moved files are properly updated
                val retargetedExternalUsages = retargetMoveUsages(externalUsages, oldToNewMap, externalUsages.size + internalUsages.size)
                val retargetedInternalUsages = retargetMoveUsages(internalUsages, oldToNewMap, externalUsages.size + internalUsages.size)
                progress.text = KotlinBundle.message("shortening.usages.progress")
                shortenUsages(retargetedExternalUsages.mergedWith(retargetedInternalUsages))
            } finally {
                oldToNewMap.values.forEach { decl -> if (decl is KtElement) unMarkAllUsages(decl) }
                progress.popState()
            }
        }

        private fun externalUsagesSorted(usages: List<K2MoveRenameUsageInfo>): Map<PsiFile, List<K2MoveRenameUsageInfo>> {
            return usages
                .filter { it.element != null } // if the element is null, it means that this external usage was moved
                .groupByFile()
                .sortedByOffset()
        }

        private fun retargetMoveUsages(
          usageInfosByFile: Map<PsiFile, List<K2MoveRenameUsageInfo>>,
          oldToNewMap: Map<PsiElement, PsiElement>,
          totalFileCount: Int
        ): Map<PsiFile, Map<PsiElement, PsiNamedElement>> {
            val progress = ProgressManager.getInstance().progressIndicator
            return usageInfosByFile.map { (file, usageInfos) ->
                progress.text2 = file.virtualFile.presentableUrl
                val usageMap = file to usageInfos.mapNotNull { usageInfo ->
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
                progress.fraction += 1 / (totalFileCount * 2).toDouble()
                usageMap
            }.toMap()
        }

        /**
         * Adds the [org.jetbrains.kotlin.resolve.ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE] qualifier prefix to this dot qualified expression if it is not there already.
         * This prefix ensures that when we qualify an expression, we do not accidentally capture local variable names that have
         * the same name as the package prefix we are located in.
         *
         * Note: This should only be called for qualified expressions that already have a package qualifier.
         */
        private fun KtDotQualifiedExpression.addRootPrefix(factory: KtPsiFactory) {
            val leftmostReceiver = getLeftMostReceiverExpression()
            if (leftmostReceiver is KtReferenceExpression) {
                if (leftmostReceiver.text == ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE) return // already has root prefix
                val replaced = factory.createExpressionByPattern("$0.$1", ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE, leftmostReceiver)
                leftmostReceiver.replace(replaced)
            }
        }

        /**
         * Removes the [ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE] from this expression that has been added by [addRootPrefix].
         */
        private fun KtDotQualifiedExpression.removeRootPrefix() {
            val selectorExpression = selectorExpression
            val receiverExpression = receiverExpression
            if (selectorExpression != null &&
                receiverExpression is KtReferenceExpression &&
                receiverExpression.text == ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE
            ) {
                this.replace(selectorExpression)
            } else if (receiverExpression is KtDotQualifiedExpression) {
                receiverExpression.removeRootPrefix()
            }
        }

        private fun shortenUsages(qualifiedUsages: Map<PsiFile, Map<PsiElement, PsiNamedElement>>) {
            val fileCount = qualifiedUsages.size
            val progress = ProgressManager.getInstance().progressIndicator
            qualifiedUsages.forEach { (file, usageMap) ->
                // We create pointers to the qualified elements here because they might get invalidated by shortening.
                val qualifiedExpressionPointers = usageMap.keys.filterIsInstance<KtDotQualifiedExpression>().map { it.createSmartPointer() }
                if (file is KtFile) {
                  shortenReferences(usageMap.keys.filterIsInstance<KtElement>())
                }
                // There are some circumstances where the reference shortener does not shorten an expression we added the root prefix to.
                // This can happen if the reference shortener is unable to add an import because the declaration's name is already
                // being used in the target package.
                // In these cases, we need to manually remove the added root prefix.
                for (pointer in qualifiedExpressionPointers) {
                    val qualifiedExpression = pointer.element ?: continue
                    qualifiedExpression.removeRootPrefix()
                }
                progress.text2 = file.virtualFile.presentableUrl
                progress.fraction += 1 / (fileCount * 2).toDouble()
            }
        }
    }
}