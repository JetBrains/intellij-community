// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.isAncestor
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler
import com.intellij.refactoring.move.moveMembers.MoveMemberHandler
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.k2.refactoring.computeWithoutAddingRedundantImports
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

sealed class K2MoveRenameUsageInfo(
  element: PsiElement,
  reference: PsiReference,
  val referencedElement: KtNamedDeclaration
) : MoveRenameUsageInfo(element, reference, referencedElement) {
    /**
     * Internal usages are usages that are inside the moved declaration.
     * On the contrary, external usages are usages to the moved declaration.
     * Example, when moving `fooBar`
     * ```
     * fun bar() {
     *   fooBar() // this usage of fooBar is external
     * }
     *
     * fun fooBar() {
     *   foo() // this usage of foo an internal usage of fooBar
     * }
     * ```
     */
    abstract val isInternal: Boolean

    open fun refresh(referenceElement: PsiElement, referencedElement: KtNamedDeclaration): K2MoveRenameUsageInfo = this

    abstract fun retarget(to: KtNamedDeclaration): PsiElement?

    class Light(
        element: PsiElement,
        reference: PsiReference,
        referencedElement: KtNamedDeclaration,
        private val wasMember: Boolean,
        private val oldContainingFqn: String?,
        private val lightElementIndex: Int,
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        override val isInternal: Boolean = false // internal usages are always Kotlin usages

        override fun retarget(to: KtNamedDeclaration): PsiElement? {
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
     * A usage that can be represented in qualified form like for example a type reference.
     */
    class Qualifiable(
      element: KtElement,
      reference: KtSimpleNameReference,
      referencedElement: KtNamedDeclaration,
      override val isInternal: Boolean
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        override fun refresh(referenceElement: PsiElement, referencedElement: KtNamedDeclaration): K2MoveRenameUsageInfo {
            if (referenceElement !is KtElement) return this
            val reference = (referenceElement.mainReference as? KtSimpleNameReference) ?: return this
           return Qualifiable(referenceElement, reference, referencedElement, isInternal)
        }

        override fun retarget(to: KtNamedDeclaration): PsiElement? {
            // shortening needs to be delayed because shortening might depend on other references
            // Lets say we for example want to replace `a.Foo` (imported) here by `b.Foo`: val x: Foo = Foo()
            // The typer reference can't shorten without changing the reference to the constructor call.
            return (element?.reference as? KtSimpleNameReference)?.bindToElement(to, KtSimpleNameReference.ShorteningMode.NO_SHORTENING)
        }
    }

    /**
     * A usage that can't be represented in qualified form like for example a call to an extension function or callable reference like
     * `::foo`.
     */
    class Unqualifiable(
      element: KtElement,
      reference: KtReference,
      referencedElement: KtNamedDeclaration,
      override val isInternal: Boolean
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        override fun refresh(referenceElement: PsiElement, referencedElement: KtNamedDeclaration): K2MoveRenameUsageInfo {
            if (referenceElement !is KtElement) return this
            val reference = referenceElement.mainReference ?: return this
            return Unqualifiable(referenceElement, reference, referencedElement, isInternal)
        }

        override fun retarget(to: KtNamedDeclaration): PsiElement? {
            val element = (element as? KtElement) ?: return element
            val containingFile = element.containingKtFile
            computeWithoutAddingRedundantImports(containingFile) { to.fqName?.let(containingFile::addImport) }
            return element
        }
    }

    companion object {
        private val LOG = Logger.getInstance(K2MoveRenameUsageInfo::class.java)

        fun find(declaration: KtNamedDeclaration): List<UsageInfo> {
            return preProcessUsages(findInternalUsages(declaration) + findExternalUsages(declaration))
        }

        /**
         * Removes unwanted usages, like for example usages through import aliases.
         */
        private fun preProcessUsages(usages: List<UsageInfo>): List<UsageInfo> {
            MoveClassHandler.EP_NAME.extensionList.forEach { handler -> handler.preprocessUsages(usages) }
            return usages
        }

        /**
         * Used to store usage info for an internal reference so that after the move the referenced can be restored.
         * @see restoreInternalUsages
         * @see K2MoveRenameUsageInfo.refresh
         */
        private var KtSimpleNameExpression.internalUsageInfo: K2MoveRenameUsageInfo? by CopyablePsiUserDataProperty(Key.create("INTERNAL_USAGE_INFO"))

        /**
         * Finds any usage inside [containingDecl]. We need these usages because when moving [containingDecl] to a different package references
         * that where previously imported by default might now require an explicit import.
         */
        @OptIn(KtAllowAnalysisFromWriteAction::class)
        private fun findInternalUsages(containingDecl: KtNamedDeclaration): List<K2MoveRenameUsageInfo> = allowAnalysisFromWriteAction {
            val usages = mutableListOf<K2MoveRenameUsageInfo>()
            containingDecl.forEachDescendantOfType<KtSimpleNameExpression> { refExpr ->
                if (refExpr is KtEnumEntrySuperclassReferenceExpression) return@forEachDescendantOfType
                if (refExpr.parent is KtThisExpression || refExpr.parent is KtSuperExpression) return@forEachDescendantOfType
                analyze(refExpr) {
                    val ref = refExpr.mainReference
                    val declSymbol = ref.resolveToSymbol() as? KtDeclarationSymbol? ?: return@forEachDescendantOfType
                    val declPsi = declSymbol.psi as? KtNamedDeclaration ?: return@forEachDescendantOfType
                    if ((refExpr.isUnqualifiable() || refExpr.isFirstInQualifiedChain()) && declPsi.needsReferenceUpdate) {
                        val usageInfo = ref.createKotlinUsageInfo(declPsi, isInternal = true)
                        usages.add(usageInfo)
                        refExpr.internalUsageInfo = usageInfo
                    }
                }
            }
          return usages
        }

        /**
         * Finds usages to [declaration] excluding the usages inside [declaration].
         */
        private fun findExternalUsages(declaration: KtNamedDeclaration): List<MoveRenameUsageInfo> {
            return ReferencesSearch.search(declaration, declaration.resolveScope).findAll()
                .filter { !declaration.isAncestor(it.element) } // exclude internal usages
                .mapNotNull { ref ->
                    if (ref is KtSimpleNameReference) {
                        ref.createKotlinUsageInfo(declaration, isInternal = false)
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

        @OptIn(KtAllowAnalysisFromWriteAction::class)
        fun KtSimpleNameExpression.isUnqualifiable(): Boolean {
            // example: a.foo() where foo is an extension function
            fun KtSimpleNameExpression.isExtensionReference(): Boolean = allowAnalysisFromWriteAction {
                analyze(this) {
                    resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.extensionReceiver != null
                }
            }

            // example: ::foo
            fun KtSimpleNameExpression.isCallableReferenceExpressionWithoutQualifier(): Boolean = allowAnalysisFromWriteAction {
                val parent = parent
                return parent is KtCallableReferenceExpression && parent.receiverExpression == null
            }

            return isExtensionReference() || isCallableReferenceExpressionWithoutQualifier()
        }

        private fun KtSimpleNameExpression.isFirstInQualifiedChain(): Boolean {
            val baseExpression = (parent as? KtCallExpression) ?: this
            val parent = baseExpression.parent
            if (parent !is KtDotQualifiedExpression) return true
            val receiver = parent.receiverExpression
            return this == receiver // if current ref is first in qualified chain the ref can be imported
        }

        private fun KtSimpleNameReference.createKotlinUsageInfo(declaration: KtNamedDeclaration, isInternal: Boolean): K2MoveRenameUsageInfo {
            val refExpr = element
            return if (refExpr.isUnqualifiable()) {
                Unqualifiable(refExpr, this, declaration, isInternal)
            } else {
                Qualifiable(refExpr, this, declaration, isInternal)
            }
        }

        internal fun retargetUsages(usages: List<UsageInfo>, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
            retargetInternalUsages(oldToNewMap)
            retargetExternalUsages(usages, oldToNewMap)
        }

        /**
         * After moving, internal usages might have become invalid, this method restores these usage infos.
         * @see internalUsageInfo
         */
        private fun restoreInternalUsages(containingDecl: KtNamedDeclaration, oldToNewMap: Map<PsiElement, PsiElement>): List<UsageInfo> {
            return containingDecl.collectDescendantsOfType<KtSimpleNameExpression>().mapNotNull { refExpr ->
                val usageInfo = refExpr.internalUsageInfo
                if (usageInfo?.element != null) return@mapNotNull usageInfo
                val referencedElement = (usageInfo as? MoveRenameUsageInfo)?.referencedElement ?: return@mapNotNull null
                val newReferencedElement = oldToNewMap[referencedElement] ?: referencedElement
                if (!newReferencedElement.isValid || newReferencedElement !is KtNamedDeclaration) return@mapNotNull null
                usageInfo.refresh(refExpr, newReferencedElement)
            }
        }

        private fun retargetInternalUsages(oldToNewMap: MutableMap<PsiElement, PsiElement>) {
            val newDeclarations = oldToNewMap.values.filterIsInstance<KtNamedDeclaration>()
            val internalUsages = newDeclarations
                .flatMap { decl -> restoreInternalUsages(decl, oldToNewMap) }
                .filterIsInstance<K2MoveRenameUsageInfo>()
                .sortedByFile()
            retargetMoveUsages(internalUsages, oldToNewMap)
        }

        private fun retargetExternalUsages(usages: List<UsageInfo>, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
            val externalUsages = usages
                .filterIsInstance<K2MoveRenameUsageInfo>()
                .filter { !it.isInternal && it.element != null } // if element is null, it means the this external usage was moved
                .sortedByFile()
            retargetMoveUsages(externalUsages, oldToNewMap)
        }

        private fun List<K2MoveRenameUsageInfo>.sortedByFile(): Map<PsiFile, List<K2MoveRenameUsageInfo>> {
            return buildMap {
                for (usageInfo in this@sortedByFile) {
                    val element = usageInfo.element
                    if (element == null) {
                        LOG.error("Could not update usage because element is invalid")
                        continue
                    }
                    val containingFile = element.containingFile
                    if (containingFile == null) {
                        LOG.error("Could not update usage because element has no containing file")
                        continue
                    }
                    val usageInfos: MutableList<K2MoveRenameUsageInfo> = getOrPut(containingFile) { mutableListOf() }
                    usageInfos.add(usageInfo)
                }
            }.mapValues { (_, value) -> value.sortedBy { it.element?.textOffset } }
        }

        @OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
        private fun retargetMoveUsages(
            usageInfosByFile: Map<PsiFile, List<K2MoveRenameUsageInfo>>,
            oldToNewMap: MutableMap<PsiElement, PsiElement>
        ) = allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                usageInfosByFile.forEach { (file, usageInfos) ->
                    // TODO instead of manually handling of qualifiable/non-qualifiable references we should invoke `bindToElement` in bulk
                    val qualifiedElements = usageInfos.mapNotNull { usageInfo ->
                        val newDeclaration = oldToNewMap[usageInfo.referencedElement] as? KtNamedDeclaration ?: usageInfo.referencedElement
                        val qualifiedReference = usageInfo.retarget(newDeclaration)
                        if (usageInfo is Qualifiable && qualifiedReference != null) {
                            qualifiedReference to newDeclaration
                        } else null
                    }.filter { it.first.isValid }.toMap()  // imports can become invalid because they are removed when binding element
                    if (file is KtFile) {
                        shortenReferences(qualifiedElements.keys.filterIsInstance<KtElement>())
                    }
                }
            }
        }
    }
}