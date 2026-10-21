// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.lexer

import com.intellij.lexer.LexerBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.tree.IElementType
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.plugins.markdown.lang.MarkdownElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class MarkdownTreeLexerBase : LexerBase() {
  @JvmField
  protected var buffer: CharSequence = ""
  private var bufferEnd = 0

  private val lexemes = ArrayList<IElementType>()
  private val startOffsets = IntArrayList()
  private val endOffsets = IntArrayList()
  private val states = IntArrayList()

  @JvmField
  protected var lexemeIndex: Int = 0

  protected fun reset(buffer: CharSequence, endOffset: Int) {
    this.buffer = buffer
    bufferEnd = endOffset
    lexemes.clear()
    startOffsets.clear()
    endOffsets.clear()
    states.clear()
    lexemeIndex = 0
  }

  protected fun addTokens(topLevelNodes: List<ASTNode>, offset: Int) {
    for (node in topLevelNodes) {
      val lineStart = offset + node.startOffset == 0 || buffer[offset + node.startOffset - 1] == '\n'
      node.accept(LexerBuildingVisitor(offset, lineStart))
    }
  }

  protected open fun addNextTokens() {}

  override fun getState(): Int = if (lexemeIndex < states.size) states.getInt(lexemeIndex) else 1

  override fun getTokenType(): IElementType? = lexemes.getOrNull(lexemeIndex)

  override fun getTokenStart(): Int = if (lexemeIndex < startOffsets.size) startOffsets.getInt(lexemeIndex) else bufferEnd

  override fun getTokenEnd(): Int = if (lexemeIndex < endOffsets.size) endOffsets.getInt(lexemeIndex) else bufferEnd

  override fun advance() {
    lexemeIndex++
    if (lexemeIndex == lexemes.size) {
      addNextTokens()
    }
  }

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = bufferEnd

  private inner class LexerBuildingVisitor(private val offset: Int, private var firstLeaf: Boolean) : RecursiveVisitor() {

    override fun visitNode(node: ASTNode) {
      ProgressManager.checkCanceled()
      if (node.startOffset == node.endOffset) {
        return
      }
      if (node.children.isEmpty()) {
        lexemes.add(MarkdownElementType.platformType(node.type))
        startOffsets.add(offset + node.startOffset)
        endOffsets.add(offset + node.endOffset)
        states.add(if (firstLeaf) 0 else 1)
        firstLeaf = false
      }
      else {
        super.visitNode(node)
      }
    }
  }
}
