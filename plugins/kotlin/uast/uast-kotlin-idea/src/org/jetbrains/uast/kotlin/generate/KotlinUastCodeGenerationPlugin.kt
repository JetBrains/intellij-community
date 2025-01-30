// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.generate

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.SmartPointerManager
import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.intentions.ImportAllMembersIntention
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.resolveToKotlinType
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.generate.UastCommentSaver
import org.jetbrains.uast.generate.UastElementFactory
import org.jetbrains.uast.toUElementOfType

private class KotlinUastCodeGenerationPlugin : KotlinUastBaseCodeGenerationPlugin() {
  override fun importMemberOnDemand(reference: UQualifiedReferenceExpression): UExpression? {
    val ktQualifiedExpression = reference.sourcePsi?.asSafely<KtDotQualifiedExpression>() ?: return null
    val selector = ktQualifiedExpression.selectorExpression ?: return null
    val ptr = SmartPointerManager.createPointer(selector)
    ImportAllMembersIntention().applyTo(ktQualifiedExpression, null)
    return ptr.element?.toUElementOfType()
  }

  override fun shortenReference(sourcePsi: KtElement): PsiElement {
    return ShortenReferences.DEFAULT.process(sourcePsi)
  }

  override fun getElementFactory(project: Project): UastElementFactory {
    return object : KotlinUastElementFactory(project) {
      override fun PsiType?.suggestName(context: PsiElement?): String {
        val resolutionFacade = (context as? KtElement)?.getResolutionFacade()
        val validator = (context as? KtElement)?.let { usedNamesFilter(it) } ?: { true }
        val ktype = resolutionFacade?.let { this?.resolveToKotlinType(it) }
        return ktype?.let { Fe10KotlinNameSuggester.suggestNamesByType(it, validator).firstOrNull() }
               ?: KotlinNameSuggester.suggestNameByName("v", validator)
      }

      override fun PsiType?.getFQname(context: PsiElement?): String? {
        val resolutionFacade = (context as? KtElement)?.getResolutionFacade()
        val ktype = resolutionFacade?.let { this?.resolveToKotlinType(it) }
        return ktype?.fqName?.toString()
      }

      override fun moveLambdaOutsideParenthesis(methodCall: KtCallExpression) {
        if (methodCall.canMoveLambdaOutsideParentheses()) {
          methodCall.moveFunctionLiteralOutsideParentheses()
        }
      }

      private fun usedNamesFilter(context: KtElement): (String) -> Boolean {
        val scope = context.getResolutionScope()
        return { name: String -> scope.findClassifier(Name.identifier(name), NoLookupLocation.FROM_IDE) == null }
      }
    }
  }

    override fun grabComments(firstResultUElement: UElement, lastResultUElement: UElement): UastCommentSaver? {
        return createUastCommentSaver(firstResultUElement, lastResultUElement)
    }
}