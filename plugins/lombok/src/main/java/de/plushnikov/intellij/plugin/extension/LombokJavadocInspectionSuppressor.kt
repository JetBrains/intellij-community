// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.extension

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.util.PsiTreeUtil
import de.plushnikov.intellij.plugin.util.DumbIncompleteModeUtil
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil

/**
 * LombokJavadocInspectionSuppressor suppresses errors for 'return' and 'param' tags in field's Javadoc
 * when those tags are used by lombok for generating getters and setters appropriately.
 *
 * It suppresses "Tag 'return' is not allowed here" error
 * in the field's Javadoc when lombok contributes getter method for that field (configured via one of @Getter, @Value, @Data)
 *
 * It suppresses "Tag 'param' is not allowed here" error
 * in the field's Javadoc when lombok contributes setter method for that field (configured via one of @Setter, @Data)
 */
class LombokJavadocInspectionSuppressor : InspectionSuppressor {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (toolId != JavadocDeclarationInspection.SHORT_NAME) return false

    val tag = PsiTreeUtil.getParentOfType(element, PsiDocTag::class.java, false) ?: return false
    val field = tag.containingComment.owner as? PsiField ?: return false

    return isLombokAvailable(field) && when (tag.name) {
      "return" -> isFirstMatchingTag(tag) { it.name == "return" } && LombokContributorUtil.isGetterContributedFor(field)
      "param" -> isParamTagForGeneratedSetter(tag, field) &&
                 isFirstMatchingTag(tag) { isParamTagForGeneratedSetter(it, field) } &&
                 LombokContributorUtil.isSetterContributedFor(field)
      else -> false
    }
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY

  private fun isLombokAvailable(field: PsiField): Boolean {
    return LombokLibraryUtil.hasLombokLibrary(field.project) || DumbIncompleteModeUtil.isIncompleteModeWithLombokAnnotation(field)
  }

  private fun isParamTagForGeneratedSetter(tag: PsiDocTag, field: PsiField): Boolean {
    return tag.valueElement?.text == field.name
  }

  private fun isFirstMatchingTag(tag: PsiDocTag, matches: (PsiDocTag) -> Boolean): Boolean {
    for (candidate in tag.containingComment.tags) {
      if (matches(candidate)) return candidate === tag
    }
    return false
  }
}
