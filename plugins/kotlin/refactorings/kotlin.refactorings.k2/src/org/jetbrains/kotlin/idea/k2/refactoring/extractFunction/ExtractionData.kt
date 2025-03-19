// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.refactoring.introduce.ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.refactoring.introduce.substringContextOrThis
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

data class ExtractionData(
    override val originalFile: KtFile,
    override val originalRange: KotlinPsiRange,
    override val targetSibling: PsiElement,
    override val duplicateContainer: PsiElement? = null,
    override val options: ExtractionOptions = ExtractionOptions.DEFAULT
) : IExtractionData {

    override val project: Project = originalFile.project
    override val originalElements: List<PsiElement> = originalRange.elements
    override val physicalElements: List<PsiElement> = originalElements.map { it.substringContextOrThis }

    override val substringInfo: ExtractableSubstringInfo?
        get() = (originalElements.singleOrNull() as? KtExpression)?.extractableSubstringInfo

    override val insertBefore: Boolean = options.extractAsProperty
            || targetSibling.getStrictParentOfType<KtDeclaration>()?.let {
        it is KtDeclarationWithBody || it is KtAnonymousInitializer
    } ?: false

    override val expressions: List<KtExpression> = originalElements.filterIsInstance<KtExpression>()

    override val codeFragmentText: String by lazy {
        val originalElements = originalElements
        when (originalElements.size) {
            0 -> ""
            1 -> originalElements.first().text
            else -> originalFile.text.substring(originalElements.first().startOffset, originalElements.last().endOffset)
        }
    }

    override val commonParent: KtElement = PsiTreeUtil.findCommonParent(physicalElements) as KtElement

    init {
        analyze(commonParent) {
            encodeReferences(true, { expr -> expr.smartCastInfo != null }) { physicalRef ->
                val resolve = if (physicalRef is KtLabelReferenceExpression) null else physicalRef.mainReference.resolve()
                val declaration =
                    resolve as? KtNamedDeclaration ?: resolve as? PsiMember
                    //if this resolves to the receiver, then retrieve corresponding callable
                    ?: ((resolve as? KtTypeReference)?.parent as? KtCallableDeclaration)?.takeIf { it.receiverTypeReference == resolve }
                declaration?.putCopyableUserData(targetKey, physicalRef)
                declaration?.let { ResolveResult<PsiElement, KtReferenceExpression>(physicalRef, declaration, declaration, physicalRef) }
            }
        }
    }

    override fun dispose() {
        expressions.forEach(::unmarkReferencesInside)
        expressions.forEach { e ->
            runReadAction {
                if (!e.isValid) return@runReadAction
                e.forEachDescendantOfType<KtSimpleNameExpression> { it.putCopyableUserData(targetKey, null) }
            }
        }
    }
}

internal val targetKey: Key<KtReferenceExpression> = Key.create("RESOLVE_RESULT")