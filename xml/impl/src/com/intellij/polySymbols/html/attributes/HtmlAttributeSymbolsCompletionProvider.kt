// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.attributes

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.XmlAttributeInsertHandler
import com.intellij.codeInsight.completion.XmlTagInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.ScrollType
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.completion.AsteriskAwarePrefixMatcher
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItemInsertHandler
import com.intellij.polySymbols.completion.PolySymbolsCompletionProviderBase
import com.intellij.polySymbols.framework.framework
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.html.HtmlDescriptorUtils.getStandardHtmlAttributeDescriptors
import com.intellij.polySymbols.html.HtmlFrameworkSymbolsSupport
import com.intellij.polySymbols.html.StandardHtmlSymbol
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.util.text.CharArrayUtil

class HtmlAttributeSymbolsCompletionProvider : PolySymbolsCompletionProviderBase<XmlAttribute>() {

  override fun getContext(position: PsiElement): XmlAttribute? =
    PsiTreeUtil.getParentOfType(position, XmlAttribute::class.java)

  override fun addCompletions(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    position: Int,
    name: String,
    queryExecutor: PolySymbolQueryExecutor,
    context: XmlAttribute,
  ) {
    val tag = context.parent ?: return
    val patchedResultSet = result.withPrefixMatcher(
      AsteriskAwarePrefixMatcher(result.prefixMatcher.cloneWithPrefix(name)))

    val providedAttributes = tag.attributes.asSequence().mapNotNull { it.name }.toMutableSet()

    val attributesFilter = HtmlFrameworkSymbolsSupport.get(queryExecutor.context.framework)
      .getAttributeNameCodeCompletionFilter(tag)

    val filteredOutStandardSymbols = getStandardHtmlAttributeDescriptors(tag)
      .map { it.name }.toMutableSet()

    processCompletionQueryResults(
      queryExecutor,
      patchedResultSet,
      HTML_ATTRIBUTES,
      name,
      position,
      context,
      providedNames = providedAttributes,
      filter = { item ->
        if (item.symbol is StandardHtmlSymbol
            && item.offset == 0
            && item.symbol?.name == item.name) {
          filteredOutStandardSymbols.remove(item.name)
          false
        }
        else {
          item.offset <= name.length
          && attributesFilter.test(name.substring(0, item.offset) + item.name)
        }
      },
      consumer = { item ->
        item.withInsertHandlerAdded(object : PolySymbolCodeCompletionItemInsertHandler {
          override fun prepare(insertionContext: InsertionContext, lookupItem: LookupElement, completeAfterInsert: Boolean) = Runnable {
            val nameEnd = removeDuplicatedNameSuffix(insertionContext)
            if (completeAfterInsert || nameEnd != insertionContext.editor.caretModel.offset) return@Runnable

            if (hasAttributeValue(insertionContext.document.charsSequence, nameEnd)) {
              moveCaretToAttributeValue(insertionContext, nameEnd)
              return@Runnable
            }

            // At this instant the file is already modified by LookupElement, so every PsiElement inside PolySymbolsRegistry is invalid
            // We need freshly constructed registry to avoid PsiInvalidElementAccessException when calling runNameMatchQuery
            val freshRegistry = PolySymbolQueryExecutorFactory.create(context,
                                                                      queryExecutor.allowResolve) // TODO Fix pointer dereference and use it here

            val fullName = name.substring(0, item.offset) + item.name
            val info = XmlTagInsertHandler.runWithTimeoutOrNull {
              val match = freshRegistry.nameMatchQuery(HTML_ATTRIBUTES, fullName)
                            .exclude(PolySymbolModifier.ABSTRACT)
                            .run()
                            .asSingleSymbol() ?: return@runWithTimeoutOrNull null
              HtmlAttributeSymbolInfo.create(fullName, freshRegistry, match, insertionContext.file)
            }
            if (info != null && shouldInsertValue(parameters, item, info)) {
              val handler = selectInsertHandler(parameters, item, info)
              handler.handleInsert(insertionContext, lookupItem)
            }
          }
        }).addToResult(parameters, patchedResultSet)
      }
    )

    providedAttributes.addAll(filteredOutStandardSymbols)

    result.runRemainingContributors(parameters) { toPass ->
      val attrName = name.removeSuffix(toPass.prefixMatcher.prefix) + toPass.lookupElement.lookupString
      if (!providedAttributes.contains(attrName) && attributesFilter.test(attrName)) {
        val element = toPass.lookupElement
        result.withPrefixMatcher(AsteriskAwarePrefixMatcher(toPass.prefixMatcher))
          .withRelevanceSorter(toPass.sorter)
          .addElement(element)
      }
    }

  }

  private fun selectInsertHandler(
    parameters: CompletionParameters,
    item: PolySymbolCodeCompletionItem,
    info: HtmlAttributeSymbolInfo,
  ): InsertHandler<LookupElement> {
    return HtmlFrameworkSymbolsSupport.get(info.symbol)
             .createAttributeInsertHandler(parameters, item, info)
           ?: XmlAttributeInsertHandler.INSTANCE
  }

  private fun shouldInsertValue(
    parameters: CompletionParameters,
    item: PolySymbolCodeCompletionItem,
    info: HtmlAttributeSymbolInfo,
  ): Boolean {
    return HtmlFrameworkSymbolsSupport.get(info.symbol)
      .shouldInsertAttributeValue(parameters, item, info)
  }

  /**
   * A pattern item can end with delimiters, which the attribute name already has after the caret.
   * For example, the `stop)` item completes `(click.|)` to `(click.stop|))`.
   * The function removes such delimiters after the caret.
   *
   * @return the end offset of the attribute name
   */
  private fun removeDuplicatedNameSuffix(context: InsertionContext): Int {
    val document = context.document
    val chars = document.charsSequence
    val caretOffset = context.editor.caretModel.offset
    val nameEnd = findAttributeNameEnd(chars, caretOffset)
    if (caretOffset <= context.startOffset || nameEnd == caretOffset) return nameEnd

    var delimitersEnd = caretOffset
    while (delimitersEnd < nameEnd && !Character.isLetterOrDigit(chars[delimitersEnd])) delimitersEnd++
    val inserted = chars.subSequence(context.startOffset, caretOffset).toString()
    val delimiters = chars.subSequence(caretOffset, delimitersEnd).toString()
    val duplicated = (delimiters.length downTo 1).firstOrNull { inserted.endsWith(delimiters.substring(0, it)) }
                     ?: return nameEnd
    document.deleteString(caretOffset, caretOffset + duplicated)
    return nameEnd - duplicated
  }

  private fun findAttributeNameEnd(chars: CharSequence, offset: Int): Int {
    var result = offset
    while (result < chars.length && ATTRIBUTE_NAME_TERMINATORS.indexOf(chars[result]) < 0) result++
    return result
  }

  private fun hasAttributeValue(chars: CharSequence, nameEnd: Int): Boolean =
    ATTRIBUTE_VALUE_STARTS.any { CharArrayUtil.regionMatches(chars, nameEnd, it) }

  private fun moveCaretToAttributeValue(context: InsertionContext, nameEnd: Int) {
    val editor = context.editor
    editor.caretModel.moveToOffset(nameEnd + 2)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    editor.selectionModel.removeSelection()
    if (context.completionChar == '=') {
      context.setAddCompletionChar(false)
    }
  }

  private companion object {
    const val ATTRIBUTE_NAME_TERMINATORS = " \t\n\r=/>\"'<"
    val ATTRIBUTE_VALUE_STARTS = listOf("=\"", "='", "={")
  }
}