// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.util.*
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.YAMLBundle
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLAnchor
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.YAMLValue
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import kotlin.math.min

private class YAMLInvalidBlockChildrenErrorAnnotator : Annotator, DumbAware {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (PsiTreeUtil.findChildrenOfType(element, OuterLanguageElement::class.java).isNotEmpty()) return

    if (anotherErrorWillBeReported(element)) return

    if (reportSameLineWarning(element, holder)) return

    if (element is YAMLBlockMappingImpl) {
      if (!isValidBlockMapChild(element.firstChild)) {
        reportWholeElementProblem(holder, element, element.firstKeyValue.key ?: element.firstKeyValue)
        return
      }

      element.children.firstOrNull { !isValidBlockMapChild(it) }?.let {
        reportSubElementProblem(holder, YAMLBundle.message("inspections.invalid.child.in.block.mapping"), it)
      }

      checkIndent(element.keyValues.toList(), holder, YAMLBundle.message("inspections.invalid.key.indent"))
    }
    if (element is YAMLBlockSequenceImpl) {
      if (!isValidBlockSequenceChild(element.firstChild)) {
        reportWholeElementProblem(holder, element, element.items.firstOrNull() ?: element)
        return
      }

      element.children.firstOrNull { !isValidBlockSequenceChild(it) }?.let {
        reportSubElementProblem(holder, YAMLBundle.message("inspections.invalid.child.in.block.sequence"), it)
      }

      checkIndent(element.items, holder, YAMLBundle.message("inspections.invalid.list.item.indent"))
    }
  }

  private fun reportWholeElementProblem(holder: AnnotationHolder, element: PsiElement, reportElement: PsiElement) {
    holder.newAnnotation(HighlightSeverity.ERROR, getMessageForParent(element))
      .range(TextRange.create(element.startOffset, endOfLine(reportElement, element))).create()
  }

  private fun endOfLine(subElement: PsiElement, whole: PsiElement): Int {
    var current = subElement
    while (true) {
      val next = PsiTreeUtil.nextLeaf(current) ?: break
      if (PsiUtilCore.getElementType(next) === YAMLTokenTypes.EOL) {
        break
      }
      current = next
      if (current.endOffset >= whole.endOffset) {
        break
      }
    }
    return min(current.endOffset, whole.endOffset)
  }

  private fun checkIndent(elements: List<PsiElement>, holder: AnnotationHolder, message: @Nls String) {
    if (elements.size > 1) {
      val firstIndent = YAMLUtil.getIndentToThisElement(elements.first())
      for (item in elements.subList(1, elements.size)) {
        if (YAMLUtil.getIndentToThisElement(item) != firstIndent) {
          reportSubElementProblem(holder, message, item)
        }
      }
    }
  }

  private fun getMessageForParent(element: PsiElement) =
    if (findNeededParent(element) is YAMLKeyValueImpl)
      YAMLBundle.message("inspections.invalid.child.in.block.mapping")
    else YAMLBundle.message("inspections.invalid.child.in.block.sequence")

  private fun isValidBlockMapChild(element: PsiElement?): Boolean =
    element.let { it is YAMLKeyValue || it is YAMLAnchor || it is LeafPsiElement }

  private fun isValidBlockSequenceChild(element: PsiElement?): Boolean =
    element.let { it is YAMLSequenceItem || it is YAMLAnchor || it is LeafPsiElement }

  private fun anotherErrorWillBeReported(element: PsiElement): Boolean {
    val kvParent = findNeededParent(element) ?: return false
    val kvGrandParent = kvParent.parentOfType<YAMLKeyValueImpl>(withSelf = false) ?: return false

    return YAMLUtil.psiAreAtTheSameLine(kvGrandParent, element)
  }

  private fun findNeededParent(element: PsiElement) = PsiTreeUtil.findFirstParent(element, true) {
    it is YAMLKeyValueImpl || it is YAMLSequenceItem
  }

  private fun reportSameLineWarning(value: PsiElement, holder: AnnotationHolder): Boolean {
    val keyValue = value.parent
    if (keyValue !is YAMLKeyValue) return false
    val key = keyValue.key ?: return false
    if (value is YAMLBlockMappingImpl) {
      val firstSubValue = value.firstKeyValue
      if (YAMLUtil.psiAreAtTheSameLine(key, firstSubValue)) {
        reportAboutSameLine(holder, value)
        return true
      }
    }
    if (value is YAMLBlockSequenceImpl) {
      val items = value.items
      if (items.isEmpty()) {
        // a very strange situation: a sequence without any item
        return true
      }
      val firstItem = items[0]
      if (YAMLUtil.psiAreAtTheSameLine(key, firstItem)) {
        reportAboutSameLine(holder, value)
        return true
      }
    }
    return false
  }

  private fun reportAboutSameLine(holder: AnnotationHolder, value: YAMLValue) {
    reportSubElementProblem(holder, YAMLBundle.message("annotator.same.line.composed.value.message"), value)
  }

  private fun reportSubElementProblem(holder: AnnotationHolder, message: @Nls String, subElement: PsiElement) {
    val firstLeaf = TreeUtil.findFirstLeaf(subElement.node)?.psi ?: return
    holder.newAnnotation(HighlightSeverity.ERROR, message)
      .range(TextRange.create(subElement.startOffset, endOfLine(firstLeaf, subElement))).create()
  }
}