// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.yaml.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.yaml.syntax.YamlSyntaxTokenTypes
import org.jetbrains.annotations.ApiStatus

/**
 * Checks that [YamlLexer] reads [newText] as one token of [tokenType] in the place of the token [oldText],
 * and that all other tokens of the file stay the same.
 * Then the leaf of the token can get the new text without a reparse of the file.
 *
 * The check lexes the line of the token from each lexical state that can start the line content.
 * Each state that gives the old token must also give the new token and the same other tokens of the line.
 * It must also give the same state at the next line start.
 * The rules of the lexer decide the tokens of a line only from the lexical state, the brace count and the text of the line.
 * The other fields of the lexer change only the actions, and the old and the new line give the same actions.
 *
 * The check refuses these cases:
 * - A token with a line break, or a line that starts in a token of the previous line.
 *   A quoted scalar can continue on the next lines.
 * - A new unescaped double quote or a new single quote in a line without one, see [keepsUnclosedQuotes].
 * - A key, a colon, `?` or `-` after the token in its line.
 *   These tokens keep their column in the lexer, and the next lines use it.
 *   So a change of the token length can change the tokens of the next lines.
 * - A line that starts with `---` or `...` at the column 0, because it can be a document marker.
 *
 * @param linePrefix the text from the line start up to the token
 * @param lineTail the text after the token up to and including the line break
 */
@ApiStatus.Internal
fun canReplaceYamlToken(
  tokenType: SyntaxElementType,
  linePrefix: CharSequence,
  oldText: CharSequence,
  newText: CharSequence,
  lineTail: CharSequence,
): Boolean {
  if (tokenType !in REPLACEABLE_TOKENS) return false
  if (newText.isEmpty() || '\n' in linePrefix || '\n' in oldText || '\n' in newText) return false
  if (lineTail.isEmpty() || lineTail.indexOf('\n') != lineTail.length - 1) return false

  val indent = linePrefix.indexOfFirst { it != ' ' && it != '\t' }.let { if (it < 0) linePrefix.length else it }
  val contentPrefix = linePrefix.subSequence(indent, linePrefix.length)
  val oldLine = StringBuilder().append(contentPrefix).append(oldText).append(lineTail)
  val newLine = StringBuilder().append(contentPrefix).append(newText).append(lineTail)
  if (!keepsUnclosedQuotes(oldLine, newLine, '\n')) return false
  if (indent == 0 && (oldLine.startsWithDocumentMarker() || newLine.startsWithDocumentMarker())) return false
  val closeBraceCount = maxOf(oldLine.count(::isCloseBrace), newLine.count(::isCloseBrace))
  if (closeBraceCount >= MAX_BRACE_COUNT) return false

  val tokenStart = contentPrefix.length
  val lexer = _YamlLexer()
  var hasOldToken = false
  for (startState in LINE_CONTENT_START_STATES[closeBraceCount]) {
    val oldTokens = lexLine(lexer, oldLine, startState)
    val index = oldTokens.tokens.indexOfToken(tokenStart)
    val oldToken = oldTokens.tokens.getOrNull(index)
    if (oldToken == null || oldToken.type !== tokenType || oldToken.length != oldText.length) continue
    if (oldTokens.tokens.subList(index + 1, oldTokens.tokens.size).any { it.type in COLUMN_TOKENS }) return false

    val expected = oldTokens.copy(tokens = oldTokens.tokens.toMutableList().also { it[index] = Token(tokenType, newText.length) })
    val newTokens = lexLine(lexer, newLine, startState)
    if (newTokens != expected || !YamlLexerStates.isRestartable(newTokens.endState)) return false
    hasOldToken = true
  }
  return hasOldToken
}

private val REPLACEABLE_TOKENS = syntaxElementTypeSetOf(
  YamlSyntaxTokenTypes.TEXT,
  YamlSyntaxTokenTypes.SCALAR_STRING,
  YamlSyntaxTokenTypes.SCALAR_DSTRING,
  YamlSyntaxTokenTypes.COMMENT,
)

/** The tokens that set `prevElementIndent` from their column. */
private val COLUMN_TOKENS = syntaxElementTypeSetOf(
  YamlSyntaxTokenTypes.SCALAR_KEY,
  YamlSyntaxTokenTypes.COLON,
  YamlSyntaxTokenTypes.QUESTION,
  YamlSyntaxTokenTypes.SEQUENCE_MARKER,
)

/** The largest brace count that a packed state holds. */
private const val MAX_BRACE_COUNT = 7

/**
 * The states that can start the content of a line, after the indent, for each count of closing braces in the line.
 * A line start state goes to the block state or to the plain text state, unless the line starts with a document marker.
 * A flow state also holds the brace count, because a closing brace goes to the block state at the count zero.
 * A brace count above the count of closing braces never gets to zero in the line.
 * So all these brace counts give the same tokens, and one of them stands for all.
 * A line of a block scalar body has no replaceable token.
 */
private val LINE_CONTENT_START_STATES: List<List<Int>> = List(MAX_BRACE_COUNT) { closeBraceCount ->
  buildList {
    add(_YamlLexer.BLOCK_STATE)
    add(_YamlLexer.POSSIBLE_PLAIN_TEXT_STATE)
    for (braceCount in 0..closeBraceCount + 1) {
      add(YamlLexerStates.pack(_YamlLexer.FLOW_STATE, 0, braceCount, false, false, false))
    }
  }
}

private fun isCloseBrace(char: Char): Boolean = char == ']' || char == '}'

/**
 * Checks that a quote without a closing quote before [oldText] also has no closing quote after the change to [newText].
 * Such a quote searches for its closing quote up to the end of the file.
 * So it is in the file only if the rest of the file has no unescaped double quote, or no single quote.
 * If [oldText] has such a quote, the file has no such quote before it, and any new text is safe.
 * Else [newText] must not have such a quote.
 * The other changes of the quotes stay in the text, and the caller lexes all of the text again.
 *
 * A backslash escapes the next char in a double-quoted scalar.
 * A run of backslashes never contains a line break, so the text gives the escapes of its quotes.
 * Both texts must start at a line start or after an indent.
 * [charAfter] is the first char after the text, which a backslash at the end of the text can escape.
 */
internal fun keepsUnclosedQuotes(oldText: CharSequence, newText: CharSequence, charAfter: Char): Boolean {
  val old = QuoteScan.of(oldText, charAfter)
  val new = QuoteScan.of(newText, charAfter)
  if (!old.hasSingleQuote && new.hasSingleQuote) return false
  if (old.hasDoubleQuote) return true
  if (new.hasDoubleQuote) return false
  // A backslash after the text continues the run of backslashes at the end of the text.
  return charAfter != '\\' || old.oddTrailingBackslashes == new.oddTrailingBackslashes
}

/**
 * The quotes of a text that a quote without a closing quote before the text can see.
 *
 * @property hasSingleQuote the text or the char after it has a single quote
 * @property hasDoubleQuote the text or the char after it has a double quote after an even run of backslashes
 * @property oddTrailingBackslashes the text ends with an odd run of backslashes
 */
private class QuoteScan(val hasSingleQuote: Boolean, val hasDoubleQuote: Boolean, val oddTrailingBackslashes: Boolean) {
  companion object {
    fun of(text: CharSequence, charAfter: Char): QuoteScan {
      var hasSingleQuote = false
      var hasDoubleQuote = false
      var escaped = false
      for (char in text) {
        when {
          escaped -> escaped = false
          char == '\\' -> escaped = true
          char == '"' -> hasDoubleQuote = true
        }
        if (char == '\'') hasSingleQuote = true
      }
      if (charAfter == '"' && !escaped) hasDoubleQuote = true
      if (charAfter == '\'') hasSingleQuote = true
      return QuoteScan(hasSingleQuote, hasDoubleQuote, escaped)
    }
  }
}

private fun CharSequence.startsWithDocumentMarker(): Boolean = startsWith("---") || startsWith("...")

private data class Token(val type: SyntaxElementType, val length: Int)

private data class LexedLine(val tokens: List<Token>, val endState: Int)

private fun List<Token>.indexOfToken(offset: Int): Int {
  var start = 0
  for ((index, token) in withIndex()) {
    if (start == offset) return index
    if (start > offset) return -1
    start += token.length
  }
  return -1
}

/**
 * Lexes [line] from [startState] with the raw [lexer].
 * It merges a run of [YamlSyntaxTokenTypes.TEXT], as [YamlLexer] does.
 * The end state is the state at the next line start.
 */
private fun lexLine(lexer: _YamlLexer, line: CharSequence, startState: Int): LexedLine {
  lexer.reset(line, 0, line.length, _YamlLexer.YYINITIAL)
  if (YamlLexerStates.isRestartable(startState)) {
    lexer.restoreState(startState)
  }
  else {
    lexer.cleanMyState()
    lexer.yybegin(startState)
  }

  val tokens = ArrayList<Token>()
  while (true) {
    val type = lexer.advance() ?: break
    val length = lexer.getTokenEnd() - lexer.getTokenStart()
    val last = tokens.lastOrNull()
    if (type === YamlSyntaxTokenTypes.TEXT && last?.type === YamlSyntaxTokenTypes.TEXT) {
      tokens[tokens.size - 1] = Token(type, last.length + length)
    }
    else {
      tokens.add(Token(type, length))
    }
  }
  return LexedLine(tokens, lexer.packState())
}
