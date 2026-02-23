// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.application.options.editor.WebEditorOptions
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TabOutScopesTracker
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.ScrollType
import com.intellij.util.text.CharArrayUtil

class XmlAttributeValueQuotationHandler(private val myStyle: AttributeValueQuotationStyle) : InsertHandler<LookupElement> {

  enum class AttributeValueQuotationStyle { QUOTES, BRACES, EQUAL_ONLY }

  companion object {
    @JvmField
    val QUOTES: XmlAttributeValueQuotationHandler = XmlAttributeValueQuotationHandler(AttributeValueQuotationStyle.QUOTES)
    @JvmField
    val BRACES: XmlAttributeValueQuotationHandler = XmlAttributeValueQuotationHandler(AttributeValueQuotationStyle.BRACES)
    @JvmField
    val EQUAL_ONLY: XmlAttributeValueQuotationHandler = XmlAttributeValueQuotationHandler(AttributeValueQuotationStyle.EQUAL_ONLY)
  }

  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val editor = context.editor
    val document = editor.document
    val caretOffset = editor.caretModel.offset

    val chars = document.charsSequence
    val hasQuotes = CharArrayUtil.regionMatches(chars, caretOffset, "=\"") ||
                    CharArrayUtil.regionMatches(chars, caretOffset, "='")
    val hasBraces = CharArrayUtil.regionMatches(chars, caretOffset, "={")
    val hasValue = hasQuotes || hasBraces

    if (!hasValue && CharArrayUtil.regionMatches(chars, caretOffset, "=")) {
      document.deleteString(caretOffset, caretOffset + 1)
    }

    var effective = myStyle
    val insertQuotes = WebEditorOptions.getInstance().isInsertQuotesForAttributeValue
    if (!insertQuotes && effective == AttributeValueQuotationStyle.QUOTES) {
      effective = AttributeValueQuotationStyle.EQUAL_ONLY
    }

    if (!hasValue) {
      val toInsert = when (effective) {
        AttributeValueQuotationStyle.BRACES -> "={}"
        AttributeValueQuotationStyle.QUOTES -> "=\"\""
        AttributeValueQuotationStyle.EQUAL_ONLY -> "="
      }

      if (caretOffset < document.textLength && "/> \n\t\r".indexOf(chars[caretOffset]) < 0) {
        document.insertString(caretOffset, "$toInsert ")
      }
      else {
        document.insertString(caretOffset, toInsert)
      }

      if ('=' == context.completionChar) {
        context.setAddCompletionChar(false)
      }
    }

    val move = when (effective) {
      AttributeValueQuotationStyle.BRACES, AttributeValueQuotationStyle.QUOTES -> 2
      AttributeValueQuotationStyle.EQUAL_ONLY -> 1
    }
    editor.caretModel.moveToOffset(caretOffset + move)
    if (effective != AttributeValueQuotationStyle.EQUAL_ONLY || hasValue) {
      TabOutScopesTracker.getInstance().registerEmptyScopeAtCaret(editor)
    }
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    editor.selectionModel.removeSelection()

    if (effective == AttributeValueQuotationStyle.BRACES) {
      AutoPopupController.getInstance(context.project).autoPopupParameterInfo(editor, null)
    } else {
      AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
    }
  }
}
