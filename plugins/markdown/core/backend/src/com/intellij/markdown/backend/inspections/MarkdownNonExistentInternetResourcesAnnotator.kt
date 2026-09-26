package com.intellij.markdown.backend.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ex.DisableInspectionToolAction
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.paths.WebReferencesAnnotatorBase
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination

class MarkdownNonExistentInternetResourcesAnnotator : WebReferencesAnnotatorBase() {
  override fun collectWebReferences(file: PsiFile): Array<WebReference> {
    if (!TrustedProjects.isProjectTrusted(file.project)) return EMPTY_ARRAY
    if (getInspection(file) == null) return EMPTY_ARRAY

    val result = mutableListOf<WebReference>()
    file.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        if (element !is MarkdownLinkDestination && element.elementType !in MarkdownTokenTypeSets.AUTO_LINKS) return
        lookForWebReference(element)?.let(result::add)
      }
    })
    return result.toTypedArray()
  }

  override fun getQuickFixes(): Array<IntentionAction> = arrayOf(DisableInspectionToolAction(requireNotNull(getInspectionKey())))

  override fun getHighlightDisplayLevel(context: PsiElement): HighlightDisplayLevel =
    InspectionProjectProfileManager.getInstance(context.project).currentProfile.getErrorLevel(
      requireNotNull(getInspectionKey()), context
    )

  override fun getErrorMessage(url: String): String = MarkdownBundle.message("markdown.unresolved.web.link.inspection.message", url)
}

private fun getInspectionKey(): HighlightDisplayKey? = HighlightDisplayKey.find(MarkdownNonExistentInternetResourceInspection.SHORT_NAME)

private fun getInspection(context: PsiElement): MarkdownNonExistentInternetResourceInspection? {
  val shortName = MarkdownNonExistentInternetResourceInspection.SHORT_NAME
  val key = getInspectionKey() ?: return null
  val profile = InspectionProjectProfileManager.getInstance(context.project).currentProfile
  if (!profile.isToolEnabled(key, context)) return null
  return profile.getUnwrappedTool(shortName, context) as? MarkdownNonExistentInternetResourceInspection
}