// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("YamlInjectedBlockFactory")
package org.jetbrains.yaml.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.DefaultInjectedLanguageBlockBuilder
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.util.SmartList
import com.intellij.util.text.escLBr


fun substituteInjectedBlocks(settings: CodeStyleSettings,
                             rawSubBlocks: List<Block>,
                             injectionHost: ASTNode,
                             wrap: Wrap?,
                             alignment: Alignment?,
                             indent: Indent?): List<Block> {
  val injectedBlocks = SmartList<Block>().apply {
    val outerBLocks = rawSubBlocks.filter { (it as? ASTBlock)?.node is OuterLanguageElement }
    YamlInjectedLanguageBlockBuilder(settings, outerBLocks).addInjectedBlocks(this, injectionHost, wrap, alignment, indent)
  }
  if (injectedBlocks.isEmpty()) return rawSubBlocks
  return injectedBlocks
}

private class YamlInjectedLanguageBlockBuilder(settings: CodeStyleSettings, val outerBlocks: List<Block>)
  : DefaultInjectedLanguageBlockBuilder(settings) {

  override fun supportsMultipleFragments(): Boolean = true

  lateinit var injectionHost: ASTNode
  lateinit var injectedFile: PsiFile

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


  override fun createInjectedBlock(node: ASTNode,
                                   originalBlock: Block,
                                   indent: Indent?,
                                   offset: Int,
                                   range: TextRange,
                                   language: Language): Block {
    val trimmedRange = trimBlank(range, range.substring(node.text))
    return YamlInjectedLanguageBlockWrapper(originalBlock, injectedToHost(trimmedRange), trimmedRange, outerBlocks, indent, language)
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

    override fun getTextRange(): TextRange = rangeInHost

    private val myBlocks by lazy(LazyThreadSafetyMode.NONE) {
      SmartList<Block>().also { result ->
        val outerBlocksQueue = ArrayDeque(outerBlocks)
        for (block in original.subBlocks) {
          myRange.intersection(block.textRange)?.let { blockRange ->
            val blockRangeInHost = injectedToHost(blockRange)
            result.addAll(outerBlocksQueue.popWhile { it.textRange.endOffset <= blockRangeInHost.startOffset })
            result.add(YamlInjectedLanguageBlockWrapper(block,
                                                        blockRangeInHost,
                                                        blockRange,
                                                        outerBlocksQueue.popWhile { blockRangeInHost.contains(it.textRange) },
                                                        block.indent, language))
          }
        }
        result.addAll(outerBlocksQueue)
      }
    }

    override fun getSubBlocks(): List<Block> = myBlocks

    override fun getWrap(): Wrap? = original.wrap
    override fun getIndent(): Indent? = indent
    override fun getAlignment(): Alignment? = original.alignment
    override fun getSpacing(child1: Block?, child2: Block): Spacing? = original.getSpacing(child1, child2)
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = original.getChildAttributes(newChildIndex)
    override fun isIncomplete(): Boolean = original.isIncomplete
    override fun isLeaf(): Boolean = original.isLeaf
    override fun getLanguage(): Language? = language
  }

  private fun <T> ArrayDeque<T>.popWhile(pred: (T) -> Boolean): List<T> {
    if (this.isEmpty()) return emptyList()
    val result = SmartList<T>()
    while (this.isNotEmpty() && pred(this.first()))
      result.add(this.removeFirst())
    return result;
  }

}