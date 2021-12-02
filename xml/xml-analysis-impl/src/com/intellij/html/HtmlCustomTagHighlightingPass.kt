// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementWalkingVisitor
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.xml.IXmlLeafElementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTokenType
import com.intellij.xml.XmlElementDescriptor

val attributeKeyMapping = mapOf<TextAttributesKey, TextAttributesKey>(
  XmlHighlighterColors.HTML_TAG_NAME to XmlHighlighterColors.HTML_CUSTOM_TAG_NAME,
  XmlHighlighterColors.XML_TAG_NAME to XmlHighlighterColors.XML_CUSTOM_TAG_NAME,
  XmlHighlighterColors.HTML_TAG to XmlHighlighterColors.HTML_CUSTOM_TAG,
  XmlHighlighterColors.XML_TAG to XmlHighlighterColors.XML_CUSTOM_TAG
)

class HtmlCustomTagHighlightingPass(val file: PsiFile, editor: Editor) : TextEditorHighlightingPass(file.project, editor.document, true) {

  private val myHolder: HighlightInfoHolder = HighlightInfoHolder(file)
  private val myHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.language, file.project, file.virtualFile)

  override fun doCollectInformation(progress: ProgressIndicator) {
    file.acceptChildren(object : XmlRecursiveElementWalkingVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        super.visitXmlTag(tag)
        val descriptor = tag.descriptor ?: return

        if (descriptor.isCustomElement || !isHtmlTagName(descriptor, tag)) {
          tag.node?.let { addLexerBasedHighlighting(it) }
        }
      }
    })
  }

  private fun isHtmlTagName(descriptor: XmlElementDescriptor, tag: XmlTag): Boolean {
    if (descriptor is HtmlElementDescriptorImpl) return true
    val nsDescriptor = tag.getNSDescriptor(tag.namespace, true)
    if (nsDescriptor is HtmlNSDescriptorImpl) {
      val htmlDescriptor = nsDescriptor.getElementDescriptorByName(tag.name)
      if (htmlDescriptor != null) return true
    }
    return false
  }

  /**
   * We have to use highlighting lexer for the mappings current token text attributes -> custom token text attributes
   * because XML/HTML highlighting lexer produces in some cases different tokens, and we have to map these tokens, and not real one
   */
  fun addLexerBasedHighlighting(node: ASTNode) {
    val (ranges, excludedRanges) = collectRanges(node)
    if (ranges.isEmpty()) return
    val highlightingLexer = myHighlighter.highlightingLexer
    val chars = node.chars
    val text = ranges.joinToString("") { it.subSequence(chars) }

    highlightingLexer.start(text)

    val startOffset = node.startOffset
    var indexOfCurrentRange = 0
    var currentRange = ranges[0]
    var offsetOfCurrentRange = 0
    while (highlightingLexer.tokenType != null) {
      if (highlightingLexer.tokenStart >= currentRange.length + offsetOfCurrentRange) {
        offsetOfCurrentRange += currentRange.length
        currentRange = ranges[++indexOfCurrentRange]
      }

      val relativeOffsetStart = (highlightingLexer.tokenStart - offsetOfCurrentRange) + currentRange.startOffset
      val relativeOffsetEnd = (highlightingLexer.tokenEnd - offsetOfCurrentRange) + currentRange.startOffset

      if (excludedRanges.none { it.containsRange(relativeOffsetStart, relativeOffsetEnd) }) {
        val absoluteStart = relativeOffsetStart + startOffset
        val absoluteEnd = relativeOffsetEnd + startOffset
        applyHighlighting(TextRange(absoluteStart, absoluteEnd), highlightingLexer.tokenType!!)
      }

      highlightingLexer.advance()
    }
  }

  private fun collectRanges(node: ASTNode): Pair<List<TextRange>, List<TextRange>> {
    var tagStarted = -1
    val includeRanges = mutableListOf<TextRange>()
    val excludedRanges = mutableListOf<TextRange>()
    for (child in node.getChildren(null)) {
      val elementType = child.elementType
      if (tagStarted == -1) {
        if (elementType == XmlTokenType.XML_START_TAG_START ||
            elementType == XmlTokenType.XML_END_TAG_START) {
          tagStarted = child.startOffsetInParent
        }
      }

      if (tagStarted >= 0) {
        if (elementType !is IXmlLeafElementType) {
          addExcludedRangesForComposite(child, excludedRanges)
        }
        else if (elementType == XmlTokenType.XML_EMPTY_ELEMENT_END || elementType == XmlTokenType.XML_TAG_END) {
          includeRanges.add(TextRange(tagStarted, child.startOffsetInParent + child.textLength))
          tagStarted = -1
        }
      }
    }
    return includeRanges to excludedRanges
  }

  fun addExcludedRangesForComposite(child: ASTNode, excludedRanges: MutableList<TextRange>) {
    if (child is LeafElement) return

    when (child.psi) {
      is XmlAttribute -> {
        for (attrPartNode in child.getChildren(null)) {
          addExcludedRangesForComposite(attrPartNode, excludedRanges)
        }
      }
      is XmlAttributeValue -> {
        for (attValuePartNode in child.getChildren(null)) {
          addExcludedRangesForComposite(attValuePartNode, excludedRanges)
        }
      }
      else -> {
        excludedRanges.add(TextRange(child.startOffsetInParent, child.startOffsetInParent + child.textLength))
      }
    }
  }

  private fun applyHighlighting(textRange: TextRange, elementType: IElementType) {
    val attributesKeys = myHighlighter.getTokenHighlights(elementType)
    val newAttributesKeys = replaceTextAttributeKeys(attributesKeys)
    if (!newAttributesKeys.contentEquals(attributesKeys)) {
      myHolder.add(highlight(textRange, newAttributesKeys))
    }
  }

  private fun replaceTextAttributeKeys(newAttributesKeys: Array<TextAttributesKey>): Array<TextAttributesKey> {
    when {
      hasKey(newAttributesKeys) -> {
        return newAttributesKeys.map { attributeKeyMapping[it] ?: it }.toTypedArray()
      }
      else -> return newAttributesKeys
    }
  }

  private fun hasKey(keys: Array<TextAttributesKey>): Boolean {
    return keys.firstOrNull { attributeKeyMapping.containsKey(it) } != null
  }

  private fun highlight(range: TextRange, key: Array<TextAttributesKey>): HighlightInfo? {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
      .severity(HighlightInfoType.SYMBOL_TYPE_SEVERITY)
      .range(range)
      .textAttributes(LayeredTextAttributes.create(colorsScheme ?: EditorColorsUtil.getGlobalOrDefaultColorScheme(), key)).create()
  }

  override fun doApplyInformationToEditor() {
    val highlights: MutableList<HighlightInfo> = ArrayList()
    for (i in 0 until myHolder.size()) {
      highlights.add(myHolder[i])
    }
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, file.textLength, highlights, colorsScheme, id)
  }
}