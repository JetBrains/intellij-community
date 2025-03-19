// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
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

  // Note that in this text escaper getStartOffsetInParent() refers to offset in host
  private class CodeFenceLiteralTextEscaper(host: MarkdownCodeFence): LiteralTextEscaper<PsiLanguageInjectionHost>(host) {
    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
      val elements = obtainFenceContent(myHost as MarkdownCodeFence, withWhitespaces = false) ?: return true
      for (element in elements) {
        val intersected = rangeInsideHost.intersection(element.textRangeInParent) ?: continue
        outChars.append(intersected.substring(myHost.text))
      }
      return true
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
      val elements = obtainFenceContent(myHost as MarkdownCodeFence, withWhitespaces = false) ?: return -1
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
      return obtainRelevantTextRange(myHost as MarkdownCodeFence)
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
      val relevantRange = obtainRelevantTextRange(element)
      val indent = MarkdownCodeFenceUtils.getIndent(element) ?: ""
      val text = collectText(element)
      val updatedText = when {
        text.isNullOrEmpty() || range.startOffset < relevantRange.startOffset -> appendIndent(content, indent)
        else -> replaceWithIndent(text, content, range = createShiftedChangeRange(range, relevantRange, text.length), indent)
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
    private fun obtainRelevantTextRange(element: MarkdownCodeFence): TextRange {
      val elements = obtainFenceContent(element, withWhitespaces = true) ?: return MarkdownCodeFenceUtils.getEmptyRange(element)
      val first = elements.first()
      val last = elements.last()
      return TextRange.create(first.startOffsetInParent, last.startOffsetInParent + last.textLength)
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
