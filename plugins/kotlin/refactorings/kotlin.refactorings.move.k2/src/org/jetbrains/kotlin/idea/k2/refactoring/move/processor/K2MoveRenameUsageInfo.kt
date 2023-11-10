// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.isAncestor
import com.intellij.refactoring.move.moveMembers.MoveMemberHandler
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

sealed class K2MoveRenameUsageInfo(
  element: PsiElement,
  reference: PsiReference,
  referencedElement: PsiElement
) : MoveRenameUsageInfo(element, reference, referencedElement) {
    abstract val isInternal: Boolean

    open fun refresh(referenceElement: PsiElement, referencedElement: KtNamedDeclaration): K2MoveRenameUsageInfo = this

    abstract fun retarget()

    class Light(
        element: PsiElement,
        reference: PsiReference,
        referencedElement: KtNamedDeclaration,
        private val wasMember: Boolean,
        private val oldContainingFqn: String?,
        private val lightElementIndex: Int,
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        override val isInternal: Boolean = false // internal usages are always Kotlin usages

        override fun retarget() {
            val element = element ?: return
            val referencedElement = (upToDateReferencedElement as? KtNamedDeclaration) ?: return
            val newLightElement = referencedElement.toLightElements()[lightElementIndex]
            if (element is PsiReferenceExpression
                && wasMember
                && newLightElement is PsiMember
                && updateJavaReference(element, oldContainingFqn, newLightElement)
            ) return
            reference?.bindToElement(newLightElement)
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
      reference: KtReference,
      referencedElement: KtNamedDeclaration,
      override val isInternal: Boolean
    ) : K2MoveRenameUsageInfo(element, reference, referencedElement) {
        override fun refresh(referenceElement: PsiElement, referencedElement: KtNamedDeclaration): K2MoveRenameUsageInfo {
            if (referenceElement !is KtElement) return this
            val reference = referenceElement.mainReference ?: return this
           return Qualifiable(referenceElement, reference, referencedElement, isInternal)
        }

        override fun retarget() {
            val referencedElement = upToDateReferencedElement ?: return
            reference?.bindToElement(referencedElement)
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

        override fun retarget() {
            val element = (element as? KtElement) ?: return
            val referencedElement = (upToDateReferencedElement as? KtNamedDeclaration) ?: return
            referencedElement.fqName?.let(element.containingKtFile::addImport)
        }
    }

    companion object {
        fun find(declaration: KtNamedDeclaration) = findInternalUsages(declaration) + findExternalUsages(declaration)

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
            fun KtDeclarationSymbol.isImported(file: KtFile): Boolean {
                val fqName = when (this) {
                    is KtClassLikeSymbol -> classIdIfNonLocal?.asSingleFqName()
                    is KtConstructorSymbol -> containingClassIdIfNonLocal?.asSingleFqName()
                    is KtCallableSymbol -> callableIdIfNonLocal?.asSingleFqName()
                    else -> null
                } ?: return false
                val importPaths = file.importDirectives.mapNotNull { it.importPath }
                return importPaths.any { fqName.isImported(it, false) }
            }

            val usages = mutableListOf<K2MoveRenameUsageInfo>()
            containingDecl.forEachDescendantOfType<KtSimpleNameExpression> { refExpr ->
                if (refExpr is KtEnumEntrySuperclassReferenceExpression) return@forEachDescendantOfType
                if (refExpr.parent is KtThisExpression || refExpr.parent is KtSuperExpression) return@forEachDescendantOfType
                analyze(refExpr) {
                    val ref = refExpr.mainReference
                    val declSymbol = ref.resolveToSymbol() as? KtDeclarationSymbol? ?: return@forEachDescendantOfType
                    val declPsi = declSymbol.psi as? KtNamedDeclaration ?: return@forEachDescendantOfType
                    if (!declSymbol.isImported(refExpr.containingKtFile) && refExpr.isImportable() && declPsi.needsReferenceUpdate) {
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
                    if (ref is KtReference) {
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

        /**
         * For example:
         * * `Foo.bar().fooBar` it will return true for `Foo` but false for `bar` and `fooBar`
         * * `x.bar()` it will return `bar` in case `bar` is an extension function
         */
        private fun KtSimpleNameExpression.isImportable(): Boolean {
            if (isUnqualifiable()) return true
            val baseExpression = (parent as? KtCallExpression) ?: this
            val parent = baseExpression.parent
            if (parent !is KtDotQualifiedExpression) return true
            return parent.selectorExpression != baseExpression // check if this expression is first in qualified chain
        }

        private fun KtReference.createKotlinUsageInfo(declaration: KtNamedDeclaration, isInternal: Boolean): K2MoveRenameUsageInfo {
            val refExpr = element
            return if (refExpr is KtSimpleNameExpression && refExpr.isUnqualifiable()) {
                Unqualifiable(refExpr, this, declaration, isInternal)
            } else {
                Qualifiable(refExpr, this, declaration, isInternal)
            }
        }

        internal fun retargetUsages(usages: List<UsageInfo>, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
            retargetInternalUsages(oldToNewMap)
            retargetExternalUsages(usages)
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
                (usageInfo as? K2MoveRenameUsageInfo)?.refresh(refExpr, newReferencedElement)
            }
        }

        private fun retargetInternalUsages(oldToNewMap: MutableMap<PsiElement, PsiElement>) {
            val newDeclarations = oldToNewMap.values.filterIsInstance<KtNamedDeclaration>()
            val restoredInternalUsages = newDeclarations.flatMap { decl -> restoreInternalUsages(decl, oldToNewMap) }
            restoredInternalUsages.forEach { (it as K2MoveRenameUsageInfo).retarget()  }
        }

        private fun retargetExternalUsages(usages: List<UsageInfo>) {
            val externalUsages = usages.filter { it is K2MoveRenameUsageInfo && !it.isInternal }
            externalUsages.forEach { (it as K2MoveRenameUsageInfo).retarget() }
        }
    }
}