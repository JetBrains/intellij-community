// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.navigation.NavigationTestUtils
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

class NavigationChecker(val file: PsiFile, val referenceTargetChecker: (PsiElement) -> Unit) {
    fun annotatedLibraryCode(): String {
        val navigableElements = collectInterestingNavigationElements()
        return NavigationTestUtils.getNavigateElementsText(file.project, navigableElements)
    }

    private fun collectInterestingNavigationElements(): List<PsiElement?> {
        val refs = collectInterestingReferences()
        return refs.map {
            val target = requireNotNull(it.resolve())
            target.navigationElement
        }
    }

    private fun collectInterestingReferences(): Collection<PsiReference> {
        val referenceContainersToReferences = LinkedHashMap<PsiElement, PsiReference>()
        val allRefs = (0 until file.textLength).flatMap { offset ->
            when (val ref = file.findReferenceAt(offset)) {
                is KtReference, is PsiReferenceExpression, is PsiJavaCodeReferenceElement -> listOf(ref)
                is PsiMultiReference -> ref.references.filterIsInstance<KtReference>()
                else -> emptyList()
            }
        }.distinct()

        for (reference in allRefs) {
            referenceContainersToReferences.addReference(reference)
        }
        return referenceContainersToReferences.values
    }

    private fun MutableMap<PsiElement, PsiReference>.addReference(ref: PsiReference) {
        if (containsKey(ref.element)) return
        val target = ref.resolve() ?: return

        referenceTargetChecker(target)

        val targetNavPsiFile = target.navigationElement.containingFile ?: return

        val targetNavFile = targetNavPsiFile.virtualFile ?: return

        if (!RootKindFilter.projectSources.matches(target.project, targetNavFile)) {
            put(ref.element, ref)
        }
    }

    companion object {
        fun checkAnnotatedCode(file: PsiFile, expectedFile: File, referenceTargetChecker: (PsiElement) -> Unit = {}) {
            val navigationChecker = NavigationChecker(file, referenceTargetChecker)
            KotlinTestUtils.assertEqualsToFile(expectedFile, navigationChecker.annotatedLibraryCode())
        }
    }
}
