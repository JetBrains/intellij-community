// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.conflicts.renderDescription
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.idea.refactoring.rename.UsageInfoWithFqNameReplacement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

fun checkClassNameShadowing(
    declaration: KtClassLikeDeclaration,
    newName: String,
    originalUsages: MutableList<UsageInfo>,
    newUsages: MutableList<UsageInfo>
) {

    fun KtType?.resolveFailed(): Boolean {
        return this == null || this is KtErrorType
    }

    val foreignReferences = mutableMapOf<Pair<PsiNamedElement, ClassId>, MutableSet<KtFile>>()
    val usageIterator = originalUsages.listIterator()
    while (usageIterator.hasNext()) {
        val usage = usageIterator.next()
        val refElement = usage.element as? KtSimpleNameExpression ?: continue
        val typeReference = refElement.getStrictParentOfType<KtTypeReference>() ?: continue

        fun createTypeFragment(type: String): KtExpressionCodeFragment {
            return KtPsiFactory(declaration.project).createExpressionCodeFragment("__foo__ as $type", typeReference)
        }

        val shortNameFragment = createTypeFragment(newName)
        val conflictingClassToClassIdPair = analyze(shortNameFragment) {
            val typeByShortName = shortNameFragment.getContentElement()?.getKtType()
            if (typeByShortName.resolveFailed()) {
                null
            } else {
                require(typeByShortName != null)
                val classSymbol = typeByShortName.expandedClassSymbol
                classSymbol?.psi as? PsiNamedElement to classSymbol?.classIdIfNonLocal
            }
        }

        if (conflictingClassToClassIdPair == null) {
            continue
        }

        val referencedClassFqName = analyze(typeReference) {
            val classSymbol = typeReference.getKtType().expandedClassSymbol
            classSymbol?.classIdIfNonLocal
        } ?: continue

        val (conflictingClass, conflictingClassId) = conflictingClassToClassIdPair
        if (conflictingClass != null && conflictingClassId != null) {
            foreignReferences.getOrPut(conflictingClass to conflictingClassId) {
                mutableSetOf()
            }.add(refElement.containingKtFile)
        }

        val newFqName = referencedClassFqName.asSingleFqName().parent().child(Name.identifier(newName))
        val codeFragment = createTypeFragment(newFqName.asString())
        analyze(codeFragment) {
            val ktType = codeFragment.getContentElement()?.getKtType()
            if (ktType.resolveFailed()) {
                usageIterator.set(UsageInfoWithFqNameReplacement(refElement, declaration, newFqName))
            } else {
                require(ktType != null)
                reportShadowing(declaration, declaration, ktType.expandedClassSymbol, refElement, newUsages)
            }
        }
    }

    foreignReferences.forEach { pair, files ->
        val newFqName = pair.second.asSingleFqName()
        for (ref in ReferencesSearch.search(pair.first, GlobalSearchScope.filesScope(declaration.project, files.map { it.virtualFile }))) {
            val refElement = ref.element as? KtSimpleNameExpression ?: continue
            newUsages.add(UsageInfoWithFqNameReplacement(refElement, declaration, newFqName))
        }
    }
}

context(KtAnalysisSession)
private fun reportShadowing(
    declaration: PsiNamedElement,
    elementToBindUsageInfoTo: PsiElement,
    candidateDescriptor: KtClassOrObjectSymbol?,
    refElement: PsiElement,
    result: MutableList<UsageInfo>
) {
    val candidate = candidateDescriptor?.psi as? PsiNamedElement ?: return
    if (declaration.parent == candidate.parent) return
    val message = KotlinBundle.message(
        "text.0.will.be.shadowed.by.1",
        declaration.renderDescription(),
        candidate.renderDescription()
    ).capitalize()
    result += BasicUnresolvableCollisionUsageInfo(refElement, elementToBindUsageInfoTo, message)
}