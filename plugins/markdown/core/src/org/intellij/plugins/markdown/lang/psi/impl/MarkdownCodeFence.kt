// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.IncorrectOperationException
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation
import org.jetbrains.annotations.ApiStatus

@Suppress("DEPRECATION")
class MarkdownCodeFence(elementType: IElementType): MarkdownCodeFenceImpl(elementType) {
  override fun accept(visitor: PsiElementVisitor) {
    when (visitor) {
      is MarkdownElementVisitor -> visitor.visitCodeFence(this)
      else -> super.accept(visitor)
    }
  }

  override fun getPresentation(): ItemPresentation {
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
        val elements = obtainFenceContent(this@MarkdownCodeFence, withWhitespaces = false) ?: return ""
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
    return MarkdownCodeFenceUtils.isAbleToAcceptInjections(this)
  }

  override fun updateText(text: String): PsiLanguageInjectionHost? {
    return ElementManipulators.handleContentChange(this, text)
  }

  override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost?> {
    return CodeFenceLiteralTextEscaper(this)
  }

  private class CodeFenceLiteralTextEscaper(host: MarkdownCodeFence): LiteralTextEscaper<MarkdownCodeFence>(host) {
    private var outSourceOffsets: IntArray? = null

    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
      val contentMask = obtainFenceContentMask(myHost)
      val hostText = myHost.text
      val sourceOffsets = IntArray(rangeInsideHost.length + 1)
      var decodedLength = 0
      for (hostOffset in rangeInsideHost.startOffset until rangeInsideHost.endOffset) {
        if (!contentMask[hostOffset]) continue
        outChars.append(hostText[hostOffset])
        if (decodedLength == 0) {
          sourceOffsets[0] = hostOffset - rangeInsideHost.startOffset
        }
        sourceOffsets[decodedLength + 1] = hostOffset - rangeInsideHost.startOffset + 1
        decodedLength++
      }
      outSourceOffsets = if (decodedLength == 0) null else sourceOffsets.copyOf(decodedLength + 1)
      return true
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
      val result = outSourceOffsets?.getOrNull(offsetInDecoded) ?: return -1
      return result + rangeInsideHost.startOffset
    }

    override fun getRelevantTextRange(): TextRange {
      return obtainRelevantTextRange(myHost)
    }

    override fun isOneLine(): Boolean = false
  }

  internal class Manipulator: AbstractElementManipulator<MarkdownCodeFence>() {
    @Throws(IncorrectOperationException::class)
    override fun handleContentChange(element: MarkdownCodeFence, range: TextRange, content: String): MarkdownCodeFence? {
      // Check if new content should break current code fence
      if (content.contains("```") || content.contains("~~~")) {
        val textElement = MarkdownPsiElementFactory.createTextElement(element.project, content)
        return if (textElement is MarkdownCodeFence) {
          element.replace(textElement) as MarkdownCodeFence
        } else null
      }
      val contentRange = obtainContentTextRange(element)
      val indent = MarkdownCodeFenceUtils.getIndent(element) ?: ""
      val text = collectText(element)
      val updatedText = when {
        text.isNullOrEmpty() || range.startOffset < contentRange.startOffset -> appendIndent(content, indent)
        else -> replaceWithIndent(text, content, range = createShiftedChangeRange(range, contentRange, text.length), indent)
      }
      val fenceElement = MarkdownPsiElementFactory.createCodeFence(element.project, element.fenceLanguage, updatedText, indent)
      return element.replace(fenceElement) as MarkdownCodeFence
    }

    private fun createShiftedChangeRange(range: TextRange, relevantRange: TextRange, textLength: Int): TextRange {
      val delta = relevantRange.startOffset
      return TextRange(
        (range.startOffset - delta).coerceIn(0, textLength),
        (range.endOffset - delta).coerceIn(0, textLength)
      )
    }

    private fun replaceWithIndent(text: String, content: String, range: TextRange, indent: String): String {
      if (content.isEmpty()) {
        return content
      }
      val prefix = text.substring(0, range.startOffset)
      val suffix = text.substring(range.endOffset)
      val lines = StringUtil.splitByLinesKeepSeparators(content).asSequence()
      return buildString {
        append(prefix)
        // If the last char of prefix was '\n', that means that new content will start on the new line,
        // so add indent before that line.
        // Otherwise, the first content line is inserted as a continuation of already existed line,
        // that should already be correctly indented.
        if (prefix.lastOrNull()?.let(StringUtil::isLineBreak) == true) {
          append(indent)
        }
        append(lines.first())
        for (line in lines.drop(1)) {
          append(indent)
          append(line)
        }
        append(suffix)
      }
    }

    private fun appendIndent(content: String, indent: String): String {
      if (indent.isEmpty()) {
        return content
      }
      val result = StringUtil.splitByLinesKeepSeparators(content).joinToString(separator = "") { indent + it }
      return when {
        StringUtil.endsWithLineBreak(content) -> result + indent
        else -> result
      }
    }

    private fun collectText(element: MarkdownCodeFence): String? {
      val elements = obtainFenceContent(element, withWhitespaces = true) ?: return null
      return buildString {
        for (child in elements) {
          append(child.text)
        }
      }
    }
  }

  companion object {
    private val FENCE_CONTENT_MASK_KEY = Key.create<CachedValue<BooleanArray>>("markdown.fence.content.mask")

    private fun obtainFenceContentMask(element: MarkdownCodeFence): BooleanArray {
      return CachedValuesManager.getCachedValue(element, FENCE_CONTENT_MASK_KEY) {
        val mask = BooleanArray(element.textLength)
        obtainFenceContent(element, withWhitespaces = false)?.forEach { child ->
          val range = child.textRangeInParent
          for (offset in range.startOffset until range.endOffset) {
            mask[offset] = true
          }
        }
        CachedValueProvider.Result.create(mask, element)
      }
    }

    private fun obtainContentTextRange(element: MarkdownCodeFence): TextRange {
      val elements = obtainFenceContent(element, withWhitespaces = true) ?: return MarkdownCodeFenceUtils.getEmptyRange(element)
      val first = elements.first()
      val last = elements.last()
      return TextRange.create(first.startOffsetInParent, last.startOffsetInParent + last.textLength)
    }

    private fun obtainRelevantTextRange(element: MarkdownCodeFence): TextRange {
      val contentRange = obtainContentTextRange(element)
      val startOffset = contentRange.startOffset
      val rangeStartOffset = if (startOffset > 0 && StringUtil.isLineBreak(element.text[startOffset - 1])) {
        startOffset - 1
      }
      else startOffset
      return TextRange.create(rangeStartOffset, contentRange.endOffset)
    }

    @ApiStatus.Experimental
    fun obtainFenceContent(element: MarkdownCodeFence, withWhitespaces: Boolean): List<PsiElement>? {
      return when {
        withWhitespaces -> CachedValuesManager.getCachedValue(element) {
          CachedValueProvider.Result.create(MarkdownCodeFenceUtils.getContent(element, true), element)
        }
        else -> CachedValuesManager.getCachedValue(element) {
          CachedValueProvider.Result.create(MarkdownCodeFenceUtils.getContent(element, false), element)
        }
      }
    }
  }
}
