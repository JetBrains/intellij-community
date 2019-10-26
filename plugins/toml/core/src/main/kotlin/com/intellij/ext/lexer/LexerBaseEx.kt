/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package com.intellij.ext.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Small utility class to ease implementing [LexerBase].
 */
abstract class LexerBaseEx : LexerBase() {
    private var state: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private lateinit var bufferSequence: CharSequence
    private var bufferEnd: Int = 0
    private var tokenType: IElementType? = null

    /**
     * Determine type of the current token (the one delimited by [tokenStart] and [tokenEnd]).
     */
    protected abstract fun determineTokenType(): IElementType?

    /**
     * Find next token location (the one starting with [tokenEnd] and ending somewhere).
     * @return end offset of the next token
     */
    protected abstract fun locateToken(start: Int): Int

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        bufferSequence = buffer
        bufferEnd = endOffset
        state = initialState

        tokenEnd = startOffset
        advance()
    }

    override fun advance() {
        tokenStart = tokenEnd
        tokenEnd = locateToken(tokenStart)
        tokenType = determineTokenType()
    }

    override fun getTokenType(): IElementType? = tokenType

    override fun getState(): Int = state

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = bufferSequence

    override fun getBufferEnd(): Int = bufferEnd
}
