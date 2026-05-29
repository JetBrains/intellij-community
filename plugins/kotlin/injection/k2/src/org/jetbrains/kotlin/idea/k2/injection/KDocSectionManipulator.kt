// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class KDocSectionManipulator : AbstractElementManipulator<KDocSection>() {
    override fun handleContentChange(
        element: KDocSection,
        range: TextRange,
        newContent: String,
    ): KDocSection? {
        val containingKDoc = PsiTreeUtil.getParentOfType(element, KDoc::class.java, /* strict = */ false)
            ?: return null

        val sectionOffsetInKDoc = element.textRange.startOffset - containingKDoc.textRange.startOffset
        val rangeInKDoc = range.shiftRight(sectionOffsetInKDoc)
        val kdocText = containingKDoc.text
        val newKDocText = kdocText.substring(0, rangeInKDoc.startOffset) +
                newContent +
                kdocText.substring(rangeInKDoc.endOffset)

        val newKDoc = KtPsiFactory(element.project).createComment(newKDocText)
        // The new KDoc may contain several sections; pick the one at the same index as the original.
        val sectionIndex = PsiTreeUtil.getChildrenOfTypeAsList(containingKDoc, KDocSection::class.java).indexOf(element)
        val newSections = PsiTreeUtil.getChildrenOfTypeAsList(newKDoc, KDocSection::class.java)
        val replacement = newSections.getOrNull(sectionIndex) ?: newSections.firstOrNull() ?: return null
        return element.replace(replacement) as? KDocSection
    }

    override fun getRangeInElement(element: KDocSection): TextRange = TextRange(0, element.textLength)
}