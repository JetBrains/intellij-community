// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection

import com.intellij.lang.ASTNode
import com.intellij.lang.DependentLanguage
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageAliases
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.parents
import org.intellij.plugins.markdown.util.MarkdownPsiUtil
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Utility functions used to work with Markdown Code Fences
 */
object MarkdownCodeFenceUtils {
  /** Check if [node] is of CODE_FENCE type */
  fun isCodeFence(node: ASTNode) = node.hasType(MarkdownTokenTypeSets.CODE_FENCE)

  /** Check if [node] inside CODE_FENCE */
  fun inCodeFence(node: ASTNode) = node.parents(withSelf = false).any { it.hasType(MarkdownTokenTypeSets.CODE_FENCE) }

  /**
   * Consider using [MarkdownCodeFence.obtainFenceContent], since it caches its result.
   *
   * Get content of code fence as list of [PsiElement]
   *
   * @param withWhitespaces defines if whitespaces (including blockquote chars `>`) should be
   * included in returned list. Otherwise, only new-line would be added.
   */
  @JvmStatic
  fun getContent(host: MarkdownCodeFence, withWhitespaces: Boolean): List<PsiElement>? {
    val children = host.firstChild?.siblings(forward = true, withSelf = true) ?: return null
    var elements = children.filter {
      (it !is OuterLanguageElement
       && (it.node.elementType == MarkdownTokenTypes.CODE_FENCE_CONTENT
           || (MarkdownPsiUtil.WhiteSpaces.isNewLine(it))
           //WHITE_SPACES may also include `>`
           || (withWhitespaces && MarkdownTokenTypeSets.WHITE_SPACES.contains(it.elementType))
          )
      )
    }.toList()
    //drop new line right after code fence lang definition
    if (elements.isNotEmpty() && MarkdownPsiUtil.WhiteSpaces.isNewLine(elements.first())) {
      elements = elements.drop(1)
    }
    //drop new right before code fence end
    if (elements.isNotEmpty() && MarkdownPsiUtil.WhiteSpaces.isNewLine(elements.last())) {
      elements = elements.dropLast(1)
    }
    //also drop a trailing blockquote-prefix WHITE_SPACE so the range stays aligned with the decoded content
    if (withWhitespaces && elements.isNotEmpty()
        && MarkdownTokenTypeSets.WHITE_SPACES.contains(elements.last().elementType)
        && !MarkdownPsiUtil.WhiteSpaces.isNewLine(elements.last())) {
      elements = elements.dropLast(1)
    }
    return elements.takeIf { it.isNotEmpty() }
  }

  /**
   * Check that code fence is reasonably formatted to accept injections
   *
   * Basically, it means that it has start and end code fence and at least
   * one line (even empty) of text.
   */
  @JvmStatic
  fun isAbleToAcceptInjections(host: MarkdownCodeFence): Boolean {
    if (host.children.all { !it.hasType(MarkdownTokenTypes.CODE_FENCE_END) }
        || host.children.all { !it.hasType(MarkdownTokenTypes.CODE_FENCE_START) }) {
      return false
    }

    val newlines = host.children.count { MarkdownPsiUtil.WhiteSpaces.isNewLine(it) }

    return newlines >= 2
  }

  /**
   * Get valid empty range (in terms of Injection) for this code fence.
   *
   * Note, that this function should be used only if [getContent]
   * returns null
   */
  fun getEmptyRange(host: MarkdownCodeFence): TextRange {
    val start = host.children.find { it.hasType(MarkdownTokenTypes.FENCE_LANG) }
                ?: host.children.find { it.hasType(MarkdownTokenTypes.CODE_FENCE_START) }

    return TextRange.from(start!!.startOffsetInParent + start.textLength + 1, 0)
  }

  /**
   * Get code fence if [element] is part of it.
   *
   * Would also work for injected elements.
   */
  fun getCodeFence(element: PsiElement): MarkdownCodeFence? {
    return InjectedLanguageManager.getInstance(element.project).getInjectionHost(element) as? MarkdownCodeFence?
           ?: PsiTreeUtil.getParentOfType(element, MarkdownCodeFence::class.java)
  }

  /**
   * Get indent for this code fence.
   *
   * If code-fence is blockquoted indent may include `>` char.
   */
  @JvmStatic
  fun getIndent(element: MarkdownCodeFence): String? {
    val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile) ?: return null
    val opening = element.children.firstOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_START }
                 ?: return ""
    val openingIndent = getIndentationInfo(opening.text)
    val offset = if (openingIndent.columns >= 4) opening.textOffset + openingIndent.length else element.textOffset
    val lineStartOffset = document.getLineStartOffset(document.getLineNumber(offset))
    return document.getText(TextRange.create(lineStartOffset, offset)).replace("[^>\\t ]".toRegex(), " ")
  }

  /** Get indentation between the active container and the opening marker. */
  @JvmStatic
  fun getOpeningIndent(element: MarkdownCodeFence): String {
    val opening = element.children.firstOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_START } ?: return ""
    return opening.text.substring(0, getIndentationInfo(opening.text).length)
  }

  @Internal
  data class IndentationInfo(val length: Int, val columns: Int)

  /** Return the leading indentation length and columns in [text]. */
  @Internal
  @JvmStatic
  fun getIndentationInfo(text: CharSequence, maxColumns: Int = Int.MAX_VALUE): IndentationInfo {
    var columns = 0
    var length = 0
    while (length < text.length && columns < maxColumns) {
      when (text[length]) {
        ' ' -> columns++
        '\t' -> columns += 4 - columns % 4
        else -> break
      }
      length++
    }
    return IndentationInfo(length, columns)
  }

  /**
   * Get the opening fence indentation in columns, relative to the active container.
   *
   * Content tokens already start after container constraints. Match that base on the opening
   * line so list continuation lines without an on-line marker still strip the correct indent.
   */
  @Internal
  @JvmStatic
  fun getOpeningIndentationColumns(element: MarkdownCodeFence): Int {
    val opening = element.children.firstOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_START } ?: return 0
    val openingIndent = getIndentationInfo(opening.text)
    if (openingIndent.columns >= 4) {
      return openingIndent.columns
    }
    val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile) ?: return 0
    val openingLineStart = document.getLineStartOffset(document.getLineNumber(opening.textOffset))
    val openingStart = opening.textOffset + openingIndent.length
    val constraintsChars = findContainerConstraintsChars(element, document)
    val indentStart = openingLineStart + constraintsChars
    if (indentStart > openingStart) {
      return 0
    }
    val indentText = document.getText(TextRange.create(indentStart, openingStart))
    return getIndentationInfo(indentText).columns.takeIf { it >= 4 } ?: 0
  }

  private fun findContainerConstraintsChars(element: MarkdownCodeFence, document: com.intellij.openapi.editor.Document): Int {
    val anchor = element.children.firstOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_CONTENT }
                 ?: element.children.firstOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_END }
                 ?: return 0
    val lineStart = document.getLineStartOffset(document.getLineNumber(anchor.textOffset))
    return (anchor.textOffset - lineStart).coerceAtLeast(0)
  }


  @Internal
  @JvmStatic
  fun hasIndent(element: MarkdownCodeFence): Boolean = !getIndent(element).isNullOrEmpty()

  @Internal
  @JvmStatic
  fun getLanguageInfoString(language: Language, context: PsiElement?): String {
    return CodeFenceLanguageProvider.EP_NAME.extensionList.firstNotNullOfOrNull {
      it.getInfoStringForLanguage(language, context)
    } ?: LanguageUtil.getBaseLanguages(language).firstNotNullOfOrNull {
      CodeFenceLanguageAliases.findMainAliasIfRegistered(it.id) ?: if (it is DependentLanguage) null else StringUtil.toLowerCase(it.id)
    } ?: StringUtil.toLowerCase(language.id)
  }
}