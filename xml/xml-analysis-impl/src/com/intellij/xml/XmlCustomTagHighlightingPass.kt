// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.INFORMATION
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.SYMBOL_TYPE_SEVERITY
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection
import com.intellij.lang.ASTNode
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementWalkingVisitor
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor
import com.intellij.xml.util.HtmlUtil

val attributeKeyMapping = mapOf<TextAttributesKey, TextAttributesKey>(
  XmlHighlighterColors.HTML_TAG_NAME to XmlHighlighterColors.HTML_CUSTOM_TAG_NAME,
  XmlHighlighterColors.XML_TAG_NAME to XmlHighlighterColors.XML_CUSTOM_TAG_NAME
)

class XmlCustomTagHighlightingPass(val file: PsiFile, editor: Editor) : TextEditorHighlightingPass(file.project, editor.document, true) {

  private val myHolder: HighlightInfoHolder = HighlightInfoHolder(file)
  private val myHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.language, file.project, file.virtualFile)

  override fun doCollectInformation(progress: ProgressIndicator) {
    file.acceptChildren(object : XmlRecursiveElementWalkingVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        super.visitXmlTag(tag)
        val descriptor = tag.descriptor ?: return

        //e.g. for unresolved tags
        if (descriptor is AnyXmlElementDescriptor && !getCustomNames().contains(tag.name)) {
          return
        }

        if (isCustomTag(descriptor, tag)) {
          tag.node?.let {
            for (child in it.getChildren(null)) {
              applyHighlighting(child, child.elementType)
            }
          }
        }
      }
    })
  }

  private fun getCustomNames() = (HtmlUtil.getEntitiesString(file, XmlEntitiesInspection.TAG_SHORT_NAME)
                             ?.let { StringUtil.split(it, ",").toSet() }
                           ?: emptySet())

  private fun isCustomTag(descriptor: XmlElementDescriptor, tag: XmlTag): Boolean {
    if (descriptor is XmlCustomElementDescriptor) return descriptor.isCustomElement

    return isHtmlLikeFile() && !isHtmlTagName(descriptor, tag)
  }

  private fun isHtmlLikeFile() = file.viewProvider.allFiles.any { it is HtmlCompatibleFile } || HtmlUtil.supportsXmlTypedHandlers(file)

  private fun isHtmlTagName(descriptor: XmlElementDescriptor, tag: XmlTag): Boolean {
    if (descriptor is HtmlElementDescriptorImpl) return true
    val nsDescriptor = tag.getNSDescriptor(tag.namespace, true)
    if (nsDescriptor is HtmlNSDescriptorImpl) {
      val htmlDescriptor = nsDescriptor.getElementDescriptorByName(tag.name)
      if (htmlDescriptor != null) {
        //make it case-sensitive
        return descriptor.name == htmlDescriptor.name
      }
    }
    return false
  }

  private fun applyHighlighting(node: ASTNode, elementType: IElementType) {
    if (node !is LeafElement) return
    val effectiveElementType = if (elementType == XmlElementType.XML_NAME) XmlElementType.XML_TAG_NAME else elementType

    val attributesKeys = myHighlighter.getTokenHighlights(effectiveElementType)
    val newAttributesKeys = replaceTextAttributeKeys(attributesKeys)
    if (!newAttributesKeys.contentEquals(attributesKeys)) {
      myHolder.add(highlight(node, newAttributesKeys))
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

  private fun highlight(node: ASTNode, key: Array<TextAttributesKey>): HighlightInfo? {
    //debug only
    @NlsSafe val description = if (ApplicationManager.getApplication().isUnitTestMode) "Custom tag name" else null
    var textAttributes = HighlightInfo.newHighlightInfo(INFORMATION)
      .severity(SYMBOL_TYPE_SEVERITY)
      .range(node)
      .textAttributes(LayeredTextAttributes.create(colorsScheme ?: EditorColorsUtil.getGlobalOrDefaultColorScheme(), key))
    if (description != null) {
      textAttributes = textAttributes.description(description)
    }
    return textAttributes.create()
  }

  override fun doApplyInformationToEditor() {
    val highlights: MutableList<HighlightInfo> = mutableListOf()
    for (i in 0 until myHolder.size()) {
      highlights.add(myHolder[i])
    }
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, file.textLength, highlights, colorsScheme, id)
  }
}