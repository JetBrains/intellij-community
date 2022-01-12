// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("YamlInjectedBlockFactory")
package org.jetbrains.yaml.formatter

import com.intellij.formatting.*
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.DefaultInjectedLanguageBlockBuilder
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import com.intellij.util.text.TextRangeUtil
import com.intellij.util.text.escLBr
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage


internal fun substituteInjectedBlocks(settings: CodeStyleSettings,
                             rawSubBlocks: List<Block>,
                             injectionHost: ASTNode,
                             wrap: Wrap?,
                             alignment: Alignment?): List<Block> {
  val injectedBlocks = SmartList<Block>().apply {
    val outerBLocks = rawSubBlocks.filter { (it as? ASTBlock)?.node is OuterLanguageElement }
    val fixedIndent = IndentImpl(Indent.Type.SPACES, false, settings.getIndentSize(YAMLFileType.YML), false, false)
    YamlInjectedLanguageBlockBuilder(settings, outerBLocks).addInjectedBlocks(this, injectionHost, wrap, alignment, fixedIndent)
  }
  if (injectedBlocks.isEmpty()) return rawSubBlocks

  injectedBlocks.addAll(0,
    rawSubBlocks.filter(injectedBlocks.first().textRange.startOffset.let { start -> { it.textRange.endOffset <= start } }))
  injectedBlocks.addAll(rawSubBlocks.filter(injectedBlocks.last().textRange.endOffset.let { end -> { it.textRange.startOffset >= end } }))

  return injectedBlocks
}

private class YamlInjectedLanguageBlockBuilder(settings: CodeStyleSettings, val outerBlocks: List<Block>)
  : DefaultInjectedLanguageBlockBuilder(settings) {

  override fun supportsMultipleFragments(): Boolean = true

  lateinit var injectionHost: ASTNode
  lateinit var injectedFile: PsiFile
  lateinit var injectionLanguage: Language

  override fun addInjectedBlocks(result: MutableList<in Block>,
                                 injectionHost: ASTNode,
                                 wrap: Wrap?,
                                 alignment: Alignment?,
                                 indent: Indent?): Boolean {
    this.injectionHost = injectionHost
    return super.addInjectedBlocks(result, injectionHost, wrap, alignment, indent)
  }

  override fun addInjectedLanguageBlocks(result: MutableList<in Block>,
                                         injectedFile: PsiFile,
                                         indent: Indent?,
                                         offset: Int,
                                         injectedEditableRange: TextRange?,
                                         shreds: List<PsiLanguageInjectionHost.Shred>) {
    this.injectedFile = injectedFile
    return super.addInjectedLanguageBlocks(result, injectedFile, indent, offset, injectedEditableRange, shreds)
  }

  override fun createBlockBeforeInjection(node: ASTNode, wrap: Wrap?, alignment: Alignment?, indent: Indent?, range: TextRange): Block? =
    super.createBlockBeforeInjection(node, wrap, alignment, indent, removeIndentFromRange(range))

  private fun removeIndentFromRange(range: TextRange): TextRange =
    trimBlank(range, range.shiftLeft(injectionHost.startOffset).substring(injectionHost.text))

  private fun injectedToHost(textRange: TextRange): TextRange =
    InjectedLanguageManager.getInstance(injectedFile.project).injectedToHost(injectedFile, textRange)

  private fun hostToInjected(textRange: TextRange): TextRange? {
    val documentWindow = PsiDocumentManager.getInstance(injectedFile.project).getCachedDocument(injectedFile) as? DocumentWindow
                         ?: return null
    return TextRange(documentWindow.hostToInjected(textRange.startOffset), documentWindow.hostToInjected(textRange.endOffset))
  }


  override fun createInjectedBlock(node: ASTNode,
                                   originalBlock: Block,
                                   indent: Indent?,
                                   offset: Int,
                                   range: TextRange,
                                   language: Language): Block {
    this.injectionLanguage = language
    val trimmedRange = trimBlank(range, range.substring(node.text))
    return YamlInjectedLanguageBlockWrapper(originalBlock, injectedToHost(trimmedRange), trimmedRange, outerBlocks, indent, YAMLLanguage.INSTANCE)
  }

  private fun trimBlank(range: TextRange, substring: String): TextRange {
    val preWS = substring.takeWhile { it.isWhitespace() }.count()
    val postWS = substring.takeLastWhile { it.isWhitespace() }.count()
    return if (preWS < range.length) range.run { TextRange(startOffset + preWS, endOffset - postWS) } else range
  }

  private inner class YamlInjectedLanguageBlockWrapper(val original: Block,
                                                       val rangeInHost: TextRange,
                                                       val myRange: TextRange,
                                                       outerBlocks: Collection<Block>,
                                                       private val indent: Indent?,
                                                       private val language: Language?) : BlockEx {

    override fun toString(): String = "YamlInjectedLanguageBlockWrapper($original, $myRange," +
                                      " rangeInRoot = $textRange '${textRange.substring(injectionHost.psi.containingFile.text).escLBr()}')"

    override fun getTextRange(): TextRange {
      val subBlocks = subBlocks
      if (subBlocks.isEmpty()) return rangeInHost
      return TextRange.create(
        subBlocks.first().textRange.startOffset.coerceAtMost(rangeInHost.startOffset),
        subBlocks.last().textRange.endOffset.coerceAtLeast(rangeInHost.endOffset))
    }

    private val myBlocks by lazy(LazyThreadSafetyMode.NONE) {
      SmartList<Block>().also { result ->
        val outerBlocksQueue = ArrayDeque(outerBlocks)
        for (block in original.subBlocks) {
          myRange.intersection(block.textRange)?.let { blockRange ->
            val blockRangeInHost = injectedToHost(blockRange)

            fun createInnerWrapper(blockRangeInHost: TextRange, blockRange: TextRange, outerNodes: Collection<Block>) =
              YamlInjectedLanguageBlockWrapper(block,
                                               blockRangeInHost,
                                               blockRange,
                                               outerNodes,
                                               replaceAbsoluteIndent(block),
                                               block.castSafelyTo<BlockEx>()?.language ?: injectionLanguage)
            
            result.addAll(outerBlocksQueue.popWhile { it.textRange.endOffset <= blockRangeInHost.startOffset })
            if (block.subBlocks.isNotEmpty()) {
              result.add(createInnerWrapper(
                blockRangeInHost,
                blockRange,
                outerBlocksQueue.popWhile { blockRangeInHost.contains(it.textRange) }))
            }
            else {
              val outer = outerBlocksQueue.popWhile { blockRangeInHost.contains(it.textRange) }
              val remainingInjectedRanges = TextRangeUtil.excludeRanges(blockRangeInHost, outer.map { it.textRange })
              val splitInjectedLeaves =
                remainingInjectedRanges.map { part -> createInnerWrapper(part, hostToInjected(part) ?: blockRange, emptyList()) }
              result.addAll((splitInjectedLeaves + outer).sortedBy { it.textRange.startOffset })
            }
          }
        }
        result.addAll(outerBlocksQueue)
      }
    }

    private fun replaceAbsoluteIndent(block: Block): Indent? = block.indent.castSafelyTo<IndentImpl>()?.takeIf { it.isAbsolute }
      ?.run { IndentImpl(type, false, spaces, isRelativeToDirectParent, isEnforceIndentToChildren) } ?:block.indent

    override fun getSubBlocks(): List<Block> = myBlocks

    override fun getWrap(): Wrap? = original.wrap
    override fun getIndent(): Indent? = indent
    override fun getAlignment(): Alignment? = original.alignment
    override fun getSpacing(child1: Block?, child2: Block): Spacing? = original.getSpacing(child1?.unwrap(), child2.unwrap())
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = original.getChildAttributes(newChildIndex)
    override fun isIncomplete(): Boolean = original.isIncomplete
    override fun isLeaf(): Boolean = original.isLeaf
    override fun getLanguage(): Language? = language
  }

  private fun Block.unwrap() = this.castSafelyTo<YamlInjectedLanguageBlockWrapper>()?.original ?: this

  private fun <T> ArrayDeque<T>.popWhile(pred: (T) -> Boolean): List<T> {
    if (this.isEmpty()) return emptyList()
    val result = SmartList<T>()
    while (this.isNotEmpty() && pred(this.first()))
      result.add(this.removeFirst())
    return result;
  }

}