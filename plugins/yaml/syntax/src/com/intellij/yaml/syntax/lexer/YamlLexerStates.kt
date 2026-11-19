// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.yaml.syntax.lexer

/**
 * The states that [YamlLexer] reports.
 *
 * At a line start, a state holds all the fields of [_YamlLexer], so the lexer can restart there.
 * Such a state is negative, or it is zero for the clean initial state.
 * So a highlighter that restarts only at zero still restarts at the clean initial state.
 * A state at another position is positive, and it holds only the lexical state.
 * The lexer cannot restart there, because it needs the column.
 * It cannot restart after a quote without a closing quote either.
 *
 * The editor highlighter keeps 16 bits of a state, so a restartable state packs its fields into 15 bits.
 * A field that does not fit makes the state not restartable.
 */
internal object YamlLexerStates {
  /** The state at the start of a token that the lexer cannot restart at, when the lexical state is [_YamlLexer.YYINITIAL]. */
  const val DIRTY_STATE: Int = 239

  private const val LEXICAL_STATE_MASK = 0b11
  private const val PLAIN_TEXT_CONTINUE_BIT = 1 shl 2
  private const val LITERAL_BLOCK_SCALAR_BIT = 1 shl 3
  private const val RETURNS_TO_BLOCK_BIT = 1 shl 4
  private const val BRACE_COUNT_SHIFT = 5
  private const val MAX_BRACE_COUNT = 0b111
  private const val INDENT_SHIFT = 8
  private const val MAX_INDENT = 0b111_1111
  private const val RESTARTABLE_BIT = 1 shl 15
  private const val PAYLOAD_MASK = RESTARTABLE_BIT - 1

  /** The lexical states at a line start, in the order of their codes. */
  private val LINE_START_STATES = intArrayOf(
    _YamlLexer.YYINITIAL,
    _YamlLexer.LINE_START_STATE,
    _YamlLexer.FLOW_STATE,
    _YamlLexer.BS_BODY_STATE,
  )

  fun isRestartable(state: Int): Boolean = state <= 0

  /**
   * Returns a state that the lexer cannot restart at. It is positive and holds only [lexicalState].
   * [_YamlLexer.YYINITIAL] is zero, and zero is the clean initial state, so it gives [DIRTY_STATE].
   */
  fun notRestartable(lexicalState: Int): Int = if (lexicalState == _YamlLexer.YYINITIAL) DIRTY_STATE else lexicalState

  /**
   * Packs the fields at a line start into a restartable state.
   *
   * The state is a 16-bit value. The cast to [Short] extends its sign to the [Int]:
   * ```
   * bit:   15 | 14 ... 8 | 7 6 5 | 4 | 3 | 2 | 1 0
   * field:  R |  indent  | brace | B | L | P | lexical state
   * ```
   * - Bits 0-1 hold the lexical state as its index in [LINE_START_STATES].
   *   The codes are 0 for [_YamlLexer.YYINITIAL], 1 for [_YamlLexer.LINE_START_STATE], 2 for [_YamlLexer.FLOW_STATE]
   *   and 3 for [_YamlLexer.BS_BODY_STATE].
   * - Bit 2 (P) holds [possiblePlainTextScalarContinue].
   * - Bit 3 (L) holds [isLiteralBlockScalar]: set for a literal block scalar `|`, clear for a folded block scalar `>`.
   * - Bit 4 (B) holds [returnsToBlock]: set if the block scalar returns to the block state, clear for the flow state.
   * - Bits 5-7 hold [braceCount], from 0 to 7.
   * - Bits 8-14 hold [indent], from 0 to 127.
   * - Bit 15 (R) is the restartable bit. It is the sign bit of the [Short], so a restartable state is negative.
   *
   * Bits 3 and 4 are set only in [_YamlLexer.BS_BODY_STATE].
   * If bits 0-14 are all zero, the function returns 0 without bit 15. This is the clean initial state.
   * If a lexical state or a field does not fit, the function returns [notRestartable].
   */
  fun pack(
    lexicalState: Int,
    indent: Int,
    braceCount: Int,
    possiblePlainTextScalarContinue: Boolean,
    isLiteralBlockScalar: Boolean,
    returnsToBlock: Boolean,
  ): Int {
    val code = lineStartCode(lexicalState)
    if (code < 0 || indent !in 0..MAX_INDENT || braceCount !in 0..MAX_BRACE_COUNT) {
      return notRestartable(lexicalState)
    }
    var payload = code or (braceCount shl BRACE_COUNT_SHIFT) or (indent shl INDENT_SHIFT)
    if (possiblePlainTextScalarContinue) payload = payload or PLAIN_TEXT_CONTINUE_BIT
    if (lexicalState == _YamlLexer.BS_BODY_STATE) {
      if (isLiteralBlockScalar) payload = payload or LITERAL_BLOCK_SCALAR_BIT
      if (returnsToBlock) payload = payload or RETURNS_TO_BLOCK_BIT
    }
    if (payload == 0) return 0
    return (payload or RESTARTABLE_BIT).toShort().toInt()
  }

  /** Returns true when [lexicalState] is one of [LINE_START_STATES], so a state with it can be restartable. */
  fun isLineStartState(lexicalState: Int): Boolean = lineStartCode(lexicalState) >= 0

  fun lexicalState(state: Int): Int = LINE_START_STATES[payload(state) and LEXICAL_STATE_MASK]

  fun indent(state: Int): Int = payload(state) shr INDENT_SHIFT

  fun braceCount(state: Int): Int = (payload(state) shr BRACE_COUNT_SHIFT) and MAX_BRACE_COUNT

  fun possiblePlainTextScalarContinue(state: Int): Boolean = payload(state) and PLAIN_TEXT_CONTINUE_BIT != 0

  fun isLiteralBlockScalar(state: Int): Boolean = payload(state) and LITERAL_BLOCK_SCALAR_BIT != 0

  fun returnsToBlock(state: Int): Boolean = payload(state) and RETURNS_TO_BLOCK_BIT != 0

  private fun payload(state: Int): Int = state and PAYLOAD_MASK

  /** Returns the index of [lexicalState] in [LINE_START_STATES], or -1 for another lexical state. */
  private fun lineStartCode(lexicalState: Int): Int = when (lexicalState) {
    _YamlLexer.YYINITIAL -> 0
    _YamlLexer.LINE_START_STATE -> 1
    _YamlLexer.FLOW_STATE -> 2
    _YamlLexer.BS_BODY_STATE -> 3
    else -> -1
  }
}
