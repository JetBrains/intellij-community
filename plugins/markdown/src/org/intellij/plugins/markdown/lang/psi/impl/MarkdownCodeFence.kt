// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.IncorrectOperationException
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils.getContent
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils.getEmptyRange
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils.getIndent
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils.isAbleToAcceptInjections
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation

open class MarkdownCodeFence(elementType: IElementType): CompositePsiElement(elementType), PsiLanguageInjectionHost, MarkdownPsiElement {
  override fun accept(visitor: PsiElementVisitor) {
    when (visitor) {
      is MarkdownElementVisitor -> visitor.visitCodeFence(this)
      else -> super.accept(visitor)
    }
  }

  open val fenceLanguage: String?
    get() = findPsiChildByType(MarkdownTokenTypes.FENCE_LANG)?.text

  override fun getPresentation(): ItemPresentation? {
    return object: MarkdownBasePresentation() {
      override fun getPresentableText(): String? {
        return when {
          !isValid -> null
          else -> "Code Fence"
        }
      }

      override fun getLocationString(): String? {
        if (!isValid) return null
        val sb = StringBuilder()
        val elements = getContent(this@MarkdownCodeFence, false) ?: return ""
        for (element in elements) {
          if (sb.isNotEmpty()) {
            sb.append("\\n")
          }
          sb.append(element.text)
          if (sb.length >= MarkdownCompositePsiElementBase.PRESENTABLE_TEXT_LENGTH) {
            break
          }
        }
        return sb.toString()
      }
    }
  }

  override fun isValidHost(): Boolean {
    return isAbleToAcceptInjections(this)
  }

  override fun updateText(text: String): PsiLanguageInjectionHost? {
    return ElementManipulators.handleContentChange(this, text)
  }

  override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost?> {
    //Note that in this text escaper getStartOffsetInParent() refers to offset in host
    return object: LiteralTextEscaper<PsiLanguageInjectionHost?>(this) {
      override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
        val elements = getContent(myHost as MarkdownCodeFence, false) ?: return true
        for (element in elements) {
          val intersected = rangeInsideHost.intersection(element.textRangeInParent) ?: continue
          outChars.append(intersected.substring(myHost.text))
        }
        return true
      }

      override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
        val elements = getContent(myHost as MarkdownCodeFence, false) ?: return -1
        var cur = 0
        for (element in elements) {
          val intersected = rangeInsideHost.intersection(element.textRangeInParent)
          if (intersected == null || intersected.isEmpty) continue
          if (cur + intersected.length == offsetInDecoded) {
            return intersected.startOffset + intersected.length
          }
          else if (cur == offsetInDecoded) {
            return intersected.startOffset
          }
          else if (cur < offsetInDecoded && cur + intersected.length > offsetInDecoded) {
            return intersected.startOffset + (offsetInDecoded - cur)
          }
          cur += intersected.length
        }
        val last = elements[elements.size - 1]
        val intersected = rangeInsideHost.intersection(last.textRangeInParent)
        if (intersected == null || intersected.isEmpty) return -1
        val result = intersected.startOffset + (offsetInDecoded - (cur - intersected.length))
        return if (rangeInsideHost.startOffset <= result && result <= rangeInsideHost.endOffset) {
          result
        }
        else -1
      }

      override fun getRelevantTextRange(): TextRange {
        val elements = getContent(myHost as MarkdownCodeFence, true) ?: return getEmptyRange(myHost)
        val first = elements[0]
        val last = elements[elements.size - 1]
        return TextRange.create(first.startOffsetInParent, last.startOffsetInParent + last.textLength)
      }

      override fun isOneLine(): Boolean = false
    }
  }

  class Manipulator: AbstractElementManipulator<MarkdownCodeFence?>() {
    @Throws(IncorrectOperationException::class)
    override fun handleContentChange(element: MarkdownCodeFence, range: TextRange, content: String): MarkdownCodeFence? {
      var actualContent = content
      if (actualContent.contains("```") || actualContent.contains("~~~")) {
        val textElement = MarkdownPsiElementFactory.createTextElement(element.project, actualContent)
        return if (textElement is MarkdownCodeFence) element.replace(textElement) as MarkdownCodeFence else null
      }
      val indent = getIndent(element)
      if (indent != null && indent.isNotEmpty()) {
        actualContent = StringUtil.splitByLinesKeepSeparators(actualContent).joinToString(separator = "") { indent + it }
        if (StringUtil.endsWithLineBreak(actualContent)) {
          actualContent += indent
        }
      }
      val fenceElement = MarkdownPsiElementFactory.createCodeFence(element.project, element.fenceLanguage, actualContent, indent)
      return element.replace(fenceElement) as MarkdownCodeFence
    }
  }
}
