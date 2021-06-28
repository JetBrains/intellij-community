package org.jetbrains.uast.kotlin.expressions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveResult
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.KotlinAbstractUExpression
import org.jetbrains.uast.kotlin.KotlinConverter
import org.jetbrains.uast.kotlin.createKDocNameSimpleNameReference
import org.jetbrains.uast.kotlin.internal.multiResolveResults
import org.jetbrains.uast.kotlin.lz

class KotlinDocUQualifiedReferenceExpression(
    override val sourcePsi: KDocName,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UQualifiedReferenceExpression, UMultiResolvable {

    override val receiver by lz {
        KotlinConverter.convertPsiElement(sourcePsi, givenParent, DEFAULT_EXPRESSION_TYPES_LIST)
                as? UExpression ?: UastEmptyExpression(givenParent)
    }
    override val selector by lz {
        createKDocNameSimpleNameReference(parentKDocName = sourcePsi, givenParent = this) ?: UastEmptyExpression(this)
    }
    override val accessType = UastQualifiedExpressionAccessType.SIMPLE

    override fun resolve(): PsiElement? = sourcePsi.reference?.resolve()

    override val resolvedName: String?
        get() = (resolve() as? PsiNamedElement)?.name

    override fun multiResolve(): Iterable<ResolveResult> = sourcePsi.multiResolveResults().asIterable()
}