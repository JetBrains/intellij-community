// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.yaml.syntax

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.LazyLexingContext
import com.intellij.platform.syntax.LazyParser
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.tokenListLexer
import com.intellij.platform.syntax.parser.ProductionMarkerList
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.platform.syntax.tree.firstLeaf
import com.intellij.platform.syntax.tree.lastLeaf
import com.intellij.platform.syntax.tree.leafLeft
import com.intellij.platform.syntax.tree.leafRight
import com.intellij.yaml.syntax.lexer.YamlLexer
import com.intellij.yaml.syntax.lexer.YamlLexerStates
import com.intellij.yaml.syntax.lexer._YamlLexer
import com.intellij.yaml.syntax.lexer.keepsUnclosedQuotes
import org.jetbrains.annotations.ApiStatus

/**
 * The data to parse the new text of one block mapping or block sequence without the rest of the file.
 * [prepareYamlBlockReparse] makes it, and [parseYamlBlock] parses the node from it.
 *
 * @property tokens the tokens of the new text, as the lexer of the file gives them
 */
@ApiStatus.Internal
class YamlBlockReparse internal constructor(
  val tokens: TokenList,
  internal val firstLineIndent: Int,
  internal val indent: Int,
  internal val insideSequence: Boolean,
)

/**
 * The lazy parser of [YamlSyntaxElementTypes.MAPPING] and [YamlSyntaxElementTypes.SEQUENCE].
 * A full parse builds these nodes at once and never collapses them.
 * So this parser only reparses a node after a change, see [prepareYamlBlockReparse].
 *
 * The client walks up from the change and checks each block node, as `BlockSupportImpl` does.
 * The client gives the node with the new text and with the parent chain of the old tree.
 * `KmpSyntaxNode.tryReparse` never reparses these nodes.
 * It takes a lazy node only if a token of the new text has the type of the node.
 *
 * The check keeps no result.
 * The lexer lexes the node at its position in the tree, see [lexBlockInTree].
 * The parse reads the indent of the node from these tokens.
 */
internal object YamlBlockLazyParser : LazyParser {
  override fun canBeReparsedIncrementally(parsingContext: LazyParsingContext): Boolean {
    val tokens = parsingContext.tokenList
    if (tokens.tokenCount == 0) return false
    val newText = tokens.tokenizedText.subSequence(tokens.getTokenStart(0), tokens.getTokenEnd(tokens.tokenCount - 1))
    return prepareYamlBlockReparse(parsingContext.node, newText, parsingContext.cancellationProvider) != null
  }

  override fun parse(parsingContext: LazyParsingContext): ProductionResult {
    val builder = parsingContext.syntaxTreeBuilder
    val node = parsingContext.node
    val reparse = blockReparseOf(node, builder.tokens) ?: fallbackReparse(builder.tokens)
    parseYamlBlock(builder, node.type, reparse)
    return prepareProduction(builder)
  }

  /**
   * Gives the tokens of the node at its position in the tree.
   * A lexer of the new text alone does not know the lexer state at the line start of the node.
   */
  override fun createLexer(lexingContext: LazyLexingContext): Lexer? {
    val node = lexingContext.node
    return lexBlockInTree(node, node.text, lexingContext.cancellationProvider)?.let { tokenListLexer(it.tokens) }
  }
}

/**
 * Returns the parse data of a lazy node from its text and its position in the tree, without the result of [prepareYamlBlockReparse].
 * A node that is not at a line start of a tree gets the data of [fallbackReparse] from the tokens of its text alone.
 * The node text must not need a parse of the node, because the node is not parsed yet.
 */
@ApiStatus.Internal
fun recreateYamlBlockReparse(node: SyntaxNode, cancellationProvider: CancellationProvider?): YamlBlockReparse {
  val text = node.text
  lexBlockInTree(node, text, cancellationProvider)?.let { return it }
  val lexed = lex(text, text.length, cancellationProvider)
  return fallbackReparse(lexed.toTokenList(0, lexed.count, text))
}

/**
 * Returns the parse data of a node that is not at a line start of a tree.
 * The node is taken as if it starts at the column zero, with its first content token at the smallest indent.
 */
private fun fallbackReparse(tokens: TokenList): YamlBlockReparse {
  var lineStart = 0
  var indent = 0
  for (index in 0 until tokens.tokenCount) {
    when (tokens.getTokenType(index)) {
      YamlSyntaxTokenTypes.EOL -> lineStart = tokens.getTokenEnd(index)
      YamlSyntaxTokenTypes.INDENT, YamlSyntaxTokenTypes.COMMENT -> Unit
      else -> {
        indent = tokens.getTokenStart(index) - lineStart
        break
      }
    }
  }
  return YamlBlockReparse(tokens, 0, indent, false)
}

/**
 * Checks that [newText] can replace the text of [node] without a reparse of the rest of the file.
 * Returns the data to parse the new text, or null.
 *
 * [node] must be a block mapping or a block sequence of the old tree.
 * Its [SyntaxNode.text] can already be the new text, but its parent chain and its siblings must be the old tree.
 * The check reads the old tokens of the node from the child of the parent at the start of the node.
 *
 * The node must be the value of a block key or of a block sequence item.
 * It must be outside of a flow collection and outside of the key of `?`.
 * It must start at a line start or after the indent of its line.
 * See the private check for the other conditions.
 *
 * @param beforeFullCheck runs once after the cheap refusals, before the walk over all leaves of the node.
 * The full check lexes the node two or three times and parses it twice.
 * A false result refuses the node.
 */
@ApiStatus.Internal
fun prepareYamlBlockReparse(
  node: SyntaxNode,
  newText: CharSequence,
  cancellationProvider: CancellationProvider?,
  beforeFullCheck: () -> Boolean = { true },
): YamlBlockReparse? {
  val parent = node.parent() ?: return null
  val insideSequence = isInsideSequence(node) ?: return null
  if (!isInBlockContext(parent)) return null
  val oldNode = parent.childByOffset(node.startOffset)?.takeIf { it.type === node.type && it.startOffset == node.startOffset } ?: return null

  val linePrefix = linePrefixOf(oldNode) ?: return null
  if (newText.isEmpty()) return null

  // The cheap refusals run before the walk over all leaves of the node.
  val charAfter = firstCharAfter(oldNode)
  if (!keepsUnclosedQuotes(oldNode.text, newText, charAfter)) return null
  if (!keepsIndentOfNextLine(oldNode.text, newText, charAfter)) return null
  val head = collectTokens(linePrefix, oldNode, untilContent = true) ?: return null
  val firstToken = findFirstContentToken(head, linePrefix.length) ?: return null
  val indent = firstToken.blockIndent(insideSequence)
  if (!hasNoLineBelowIndent(newText, indent, insideSequence)) return null
  if (!beforeFullCheck()) return null

  val oldTokens = collectTokens(linePrefix, oldNode, untilContent = false) ?: return null
  val tail = collectTail(oldNode) ?: return null
  return prepareYamlBlockReparse(node.type, insideSequence, linePrefix, oldTokens, newText, tail, firstToken, indent, cancellationProvider)
}

/**
 * Returns the indent before [node] in its line.
 * Returns null if the node does not start at a line start or after the indent of its line.
 */
private fun linePrefixOf(node: SyntaxNode): CharSequence? {
  val prefixLeaf = node.leafLeft()
  val linePrefix = when (prefixLeaf?.type) {
    YamlSyntaxTokenTypes.INDENT -> {
      val beforeIndent = prefixLeaf.leafLeft()
      if (beforeIndent != null && beforeIndent.type !== YamlSyntaxTokenTypes.EOL) return null
      prefixLeaf.text
    }
    YamlSyntaxTokenTypes.EOL -> ""
    else -> return null
  }
  return if (linePrefix.all { it == ' ' || it == '\t' }) linePrefix else null
}

/**
 * Returns the parse data of a lazy node from its position in the tree, without the old tokens.
 * A lazy node exists only after a successful check of its text.
 * So the lexing of its line prefix, its text and its tail from [PLAIN_TEXT_OFF] gives the tokens of a full lexing.
 * Returns null if the node is not the value of a block key or of a block sequence item, at a line start.
 */
private fun lexBlockInTree(node: SyntaxNode, text: CharSequence, cancellationProvider: CancellationProvider?): YamlBlockReparse? {
  val insideSequence = isInsideSequence(node) ?: return null
  val linePrefix = linePrefixOf(node) ?: return null
  val tail = collectTail(node) ?: return null
  val prefixLength = linePrefix.length
  val end = prefixLength + text.length
  val buffer = StringBuilder(end + tail.length).append(linePrefix).append(text).append(tail)
  val lexed = lex(buffer, buffer.length, cancellationProvider)
  val firstIndex = lexed.indexOf(prefixLength)
  val endIndex = lexed.boundaryIndex(end)
  if (firstIndex < 0 || endIndex < 0) return null
  val firstToken = findFirstContentToken(lexed, endIndex, prefixLength) ?: return null
  return YamlBlockReparse(lexed.toTokenList(firstIndex, endIndex, text), prefixLength, firstToken.blockIndent(insideSequence), insideSequence)
}

/**
 * Returns the parse data of a lazy node from the tokens of [lexBlockInTree] and the position of the node in the tree.
 * Returns null if the node is not the value of a block key or of a block sequence item, at a line start.
 */
private fun blockReparseOf(node: SyntaxNode, tokens: TokenList): YamlBlockReparse? {
  val insideSequence = isInsideSequence(node) ?: return null
  val prefixLength = linePrefixOf(node)?.length ?: return null
  val firstToken = findFirstContentToken(
    tokens.tokenCount, prefixLength, { tokens.getTokenStart(it) + prefixLength }, { tokens.getTokenEnd(it) + prefixLength }, tokens::getTokenType,
  ) ?: return null
  return YamlBlockReparse(tokens, prefixLength, firstToken.blockIndent(insideSequence), insideSequence)
}

/** Returns true for the value of a sequence item, false for the value of a key, or null for another node. */
private fun isInsideSequence(node: SyntaxNode): Boolean? = when (node.parent()?.type) {
  YamlSyntaxElementTypes.KEY_VALUE_PAIR -> false
  YamlSyntaxElementTypes.SEQUENCE_ITEM -> true
  else -> null
}

/**
 * Checks that the indent of the line of the next token stays the same.
 * The node can end with this indent, after its last line break.
 * A longer indent can move the next token into the node in a full parse, and the tokens after the node stay the same.
 * A line break after the node, or the end of the file, makes the indent unimportant.
 */
private fun keepsIndentOfNextLine(oldText: CharSequence, newText: CharSequence, charAfter: Char): Boolean {
  if (charAfter == '\n') return true
  val oldLastLine = oldText.subSequence(oldText.lastIndexOf('\n') + 1, oldText.length)
  val newLastLine = newText.subSequence(newText.lastIndexOf('\n') + 1, newText.length)
  if (!oldLastLine.isIndent() && !newLastLine.isIndent()) return true
  return oldLastLine.contentEquals(newLastLine)
}

private fun CharSequence.isIndent(): Boolean = all { it == ' ' || it == '\t' }

/** Returns the first char after [node], or a line break at the end of the file. */
private fun firstCharAfter(node: SyntaxNode): Char {
  var leaf = node.leafRight()
  while (leaf != null) {
    leaf.text.firstOrNull()?.let { return it }
    leaf = leaf.leafRight()
  }
  return '\n'
}

/**
 * Checks that [node] and its parents are not in a flow collection and not in the key of an explicit key `?`.
 * There the parser stops a block at a comma, a closing brace or a colon, and the lexer has another state.
 */
private fun isInBlockContext(node: SyntaxNode): Boolean {
  var current: SyntaxNode? = node
  while (current != null) {
    when (current.type) {
      YamlSyntaxElementTypes.HASH, YamlSyntaxElementTypes.ARRAY -> return false
      YamlSyntaxElementTypes.KEY_VALUE_PAIR -> if (current.isExplicitKeyValue()) return false
    }
    current = current.parent()
  }
  return true
}

/** An anchor or a tag before `?` is a child of the pair, so `?` is not always its first leaf. */
private fun SyntaxNode.isExplicitKeyValue(): Boolean =
  generateSequence(firstChild()) { it.nextSibling() }.any { it.type === YamlSyntaxTokenTypes.QUESTION }

/** A client tree can give the whitespace token of the lexer the platform whitespace type. */
private fun SyntaxNode.tokenType(): SyntaxElementType = if (type === SyntaxTokenTypes.WHITE_SPACE) YamlSyntaxTokenTypes.WHITESPACE else type

/**
 * Returns the tokens of [linePrefix] and of [node], as the tree has them.
 * If [untilContent] is true, the tokens end at the first token that is not a comment, a line break or an indent.
 */
private fun collectTokens(linePrefix: CharSequence, node: SyntaxNode, untilContent: Boolean): TokenList? {
  var starts = IntArray(64)
  val types = ArrayList<SyntaxElementType>()
  val text = StringBuilder(linePrefix.length + node.endOffset - node.startOffset)
  fun add(type: SyntaxElementType, chars: CharSequence) {
    if (chars.isEmpty()) return
    if (types.size + 1 >= starts.size) starts = starts.copyOf(starts.size * 2)
    starts[types.size] = text.length
    types.add(type)
    text.append(chars)
  }
  if (linePrefix.isNotEmpty()) add(YamlSyntaxTokenTypes.INDENT, linePrefix)
  val last = node.lastLeaf() ?: node
  var leaf = node.firstLeaf() ?: node
  while (true) {
    val type = leaf.tokenType()
    add(type, leaf.text)
    if (untilContent && leaf.text.isNotEmpty() && type !in HEAD_TOKENS) break
    if (leaf === last || leaf.startOffset >= last.startOffset) break
    leaf = leaf.leafRight() ?: return null
  }
  starts[types.size] = text.length
  return TokenList(starts, types.toTypedArray(), types.size, text)
}

/**
 * Returns the text after [node] up to the end of the line of the next leaf that is not a space, a line break or a comment.
 * The parser looks at this leaf to end the node, and the lexer looks at the next line after a line break.
 * The text also has the first char after this line, if the file has one.
 * The lexer gives the state at the next line start only before a token there.
 * Returns null if this text is longer than [MAX_TAIL_LENGTH].
 * The search for the line break and each append stay in this limit.
 */
private fun collectTail(node: SyntaxNode): CharSequence? {
  val tail = StringBuilder()
  var contentFound = false
  var lineEnd = -1
  var leaf = node.leafRight()
  while (leaf != null) {
    val chars = leaf.text
    val rest = MAX_TAIL_LENGTH - tail.length
    if (lineEnd >= 0) {
      if (chars.isNotEmpty()) return if (rest > 0) tail.append(chars[0]) else null
    }
    else if (contentFound) {
      val lineBreak = chars.indexOfLineBreak(minOf(chars.length, rest))
      if (lineBreak >= 0) {
        tail.append(chars, 0, lineBreak + 1)
        lineEnd = tail.length
        if (lineBreak + 1 < chars.length) return if (lineBreak + 1 < rest) tail.append(chars[lineBreak + 1]) else null
        leaf = leaf.leafRight()
        continue
      }
    }
    else if (leaf.tokenType() !in YamlSyntaxElementTypes.BLANK_ELEMENTS) {
      contentFound = true
    }
    if (chars.length > rest) return null
    tail.append(chars)
    leaf = leaf.leafRight()
  }
  return tail
}

/** Returns the index of the first line break before [end], or -1. */
private fun CharSequence.indexOfLineBreak(end: Int): Int {
  for (index in 0 until end) {
    if (this[index] == '\n') return index
  }
  return -1
}

private const val MAX_TAIL_LENGTH = 64 * 1024

/** The tokens that [findFirstContentToken] skips before the first content token. */
private val HEAD_TOKENS = syntaxElementTypeSetOf(YamlSyntaxTokenTypes.COMMENT, YamlSyntaxTokenTypes.EOL, YamlSyntaxTokenTypes.INDENT)

/**
 * Parses the new text of a block mapping or a block sequence as one node of [nodeType].
 * The builder must hold the tokens of [YamlBlockReparse.tokens].
 */
@ApiStatus.Internal
fun parseYamlBlock(builder: SyntaxTreeBuilder, nodeType: SyntaxElementType, reparse: YamlBlockReparse) {
  val root = builder.mark()
  YamlParser(builder).parseBlockCollection(reparse.firstLineIndent, reparse.indent, reparse.insideSequence)
  while (!builder.eof()) {
    builder.advanceLexer()
  }
  root.done(nodeType)
}

/**
 * Checks that a full parse of the file gives the same tree outside of a block mapping or a block sequence.
 * The full parse must also give one node of the same type for the new text of the node.
 * Then the new text can be parsed alone with the returned data.
 *
 * The caller checks the position of the node in the tree, the quotes and the indent of the lines of [newText].
 *
 * The lexer state at the line start of the node is not in the tree.
 * The check knows that the brace count is zero, because the node is not in a flow collection.
 * The plain text flag can be set from an earlier line, so the check lexes the old text with this flag off and on.
 * The old tokens of the tree tell which of the two states the lexer had.
 * If both states give the old tokens, both states must give the same new tokens.
 * The lexing with the flag on runs only if the old or the new first token can continue the plain text.
 * So the first token of the node after its comments must be a key, `-` or `?`.
 * These tokens set the column of the lexer.
 *
 * The check refuses these cases:
 * - Different tokens or a different lexer state at the start of the line after the node.
 * - A new text with another first content token indent than the old text.
 * - A new text that the parser does not read as one node of [nodeType] up to the end of the text.
 * - A parse of the new text alone that gives another tree inside the node than the parse with the tail.
 * - A statement that starts in the line of the statement before it, as `e: t` in `[a] e: t`.
 *   It takes the indent of its block, and a full parse can give the block a smaller indent than the column of its first token.
 *
 * @param nodeType [YamlSyntaxElementTypes.MAPPING] or [YamlSyntaxElementTypes.SEQUENCE]
 * @param insideSequence true if the node is the value of a sequence item
 * @param linePrefix the indent before the node in its first line
 * @param oldTokens the tokens of the tree for [linePrefix] and the old text of the node
 * @param newText the new text of the node
 * @param tail the text after the node up to the end of the line of the next token that is not a space, a line break or a comment
 * @param firstToken the first content token of the old text, see [findFirstContentToken]
 * @param indent the smallest indent of the lines of the node
 */
private fun prepareYamlBlockReparse(
  nodeType: SyntaxElementType,
  insideSequence: Boolean,
  linePrefix: CharSequence,
  oldTokens: TokenList,
  newText: CharSequence,
  tail: CharSequence,
  firstToken: FirstToken,
  indent: Int,
  cancellationProvider: CancellationProvider?,
): YamlBlockReparse? {
  if (nodeType !== YamlSyntaxElementTypes.MAPPING && nodeType !== YamlSyntaxElementTypes.SEQUENCE) return null

  val prefixLength = linePrefix.length
  val oldWithPrefix = oldTokens.tokenizedText
  val oldBuffer = StringBuilder(oldWithPrefix.length + tail.length).append(oldWithPrefix).append(tail)
  val newBuffer = StringBuilder(prefixLength + newText.length + tail.length).append(linePrefix).append(newText).append(tail)
  val oldEnd = oldWithPrefix.length
  val newEnd = prefixLength + newText.length
  val oldBoundary = nextLineStart(oldBuffer, oldEnd)
  val newBoundary = oldBoundary - oldEnd + newEnd

  val oldLexed = lex(oldBuffer, oldBoundary, cancellationProvider)
  if (!oldLexed.startsWith(oldTokens)) return null
  val newLexed = lex(newBuffer, newBuffer.length, cancellationProvider)
  val newEndIndex = newLexed.boundaryIndex(newEnd)
  if (newLexed.indexOf(prefixLength) < 0 || newEndIndex < 0) return null
  val newFirstToken = findFirstContentToken(newLexed, newEndIndex, prefixLength) ?: return null
  if (newFirstToken.blockIndent(insideSequence) != indent) return null
  if (!sameTokensAndState(oldLexed, oldEnd, oldBoundary, newLexed, newEnd, newBoundary)) return null

  if (firstToken.canContinuePlainText || newFirstToken.canContinuePlainText) {
    val elementIndents = mutableSetOf(0)
    oldLexed.addHeadLineIndents(elementIndents)
    newLexed.addHeadLineIndents(elementIndents)
    for (elementIndent in elementIndents) {
      val plainTextOn = plainTextOn(elementIndent) ?: return null
      when (lexUntilSameState(oldBuffer, oldLexed, oldBoundary, plainTextOn, cancellationProvider)) {
        Convergence.DIFFERENT_TOKENS -> Unit // The old tokens show that the lexer had another state.
        Convergence.NOT_REACHED -> return null
        Convergence.SAME_STATE -> {
          if (lexUntilSameState(newBuffer, newLexed, newBoundary, plainTextOn, cancellationProvider) != Convergence.SAME_STATE) return null
        }
      }
    }
  }

  val firstIndex = newLexed.indexOf(prefixLength)
  val withTail = parseWithTail(newBuffer, newLexed, firstIndex, prefixLength, newText.length, nodeType, indent, insideSequence, cancellationProvider)
                 ?: return null
  val reparse = YamlBlockReparse(newLexed.toTokenList(firstIndex, newLexed.boundaryIndex(newEnd), newText), prefixLength, indent, insideSequence)
  val alone = SyntaxTreeBuilderFactory.builder(newText, reparse.tokens, YamlSyntaxDefinition.whitespaces, YamlSyntaxDefinition.comments)
    .withCancellationProvider(cancellationProvider)
    .build()
  parseYamlBlock(alone, nodeType, reparse)
  return if (sameInnerMarkers(prepareProduction(alone).productionMarkers, withTail.productionMarkers)) reparse else null
}

/** The state at the line start of the node when the plain text flag is off. */
private val PLAIN_TEXT_OFF = YamlLexerStates.pack(_YamlLexer.LINE_START_STATE, 0, 0, false, false, false)

/**
 * Returns the state at the line start of the node when the plain text flag of an earlier line is on, or null.
 * [elementIndent] is the column of the last key, `:`, `?` or `-` before the node.
 * A line with an indent at or below [elementIndent] turns the flag off.
 * Up to the first content token of the node, only these lines need [elementIndent], see [LexedText.addHeadLineIndents].
 * The first content token sets the element indent of the lexer.
 */
private fun plainTextOn(elementIndent: Int): Int? =
  YamlLexerStates.pack(_YamlLexer.LINE_START_STATE, elementIndent, 0, true, false, false).takeIf { YamlLexerStates.isRestartable(it) }

private class FirstToken(val type: SyntaxElementType, val column: Int, val canContinuePlainText: Boolean) {
  /** Returns the smallest indent of the lines of the node. A sequence outside of a sequence item gets one more column. */
  fun blockIndent(insideSequence: Boolean): Int = column + if (type === YamlSyntaxTokenTypes.SEQUENCE_MARKER && !insideSequence) 1 else 0
}

/**
 * Returns the first token of the node that is not a comment, a line break or an indent.
 * It must be a key, `-` or `?` at the start of the content of its line.
 * [FirstToken.canContinuePlainText] is false if a line up to this token has no indent, because such a line turns the plain text flag off.
 */
private fun findFirstContentToken(tokens: TokenList, prefixLength: Int): FirstToken? =
  findFirstContentToken(tokens.tokenCount, prefixLength, tokens::getTokenStart, tokens::getTokenEnd, tokens::getTokenType)

/** Returns the first content token of the tokens of [lexed] up to [endIndex], see [findFirstContentToken]. */
private fun findFirstContentToken(lexed: LexedText, endIndex: Int, prefixLength: Int): FirstToken? =
  findFirstContentToken(endIndex, prefixLength, { lexed.starts[it] }, { if (it + 1 < lexed.count) lexed.starts[it + 1] else lexed.endOffset }, { lexed.types[it] })

private inline fun findFirstContentToken(
  count: Int,
  prefixLength: Int,
  start: (Int) -> Int,
  end: (Int) -> Int,
  type: (Int) -> SyntaxElementType?,
): FirstToken? {
  var lineStart = 0
  var canContinuePlainText = prefixLength > 0
  var index = 0
  while (index < count && start(index) < prefixLength) index++
  if (index == count || start(index) != prefixLength) return null
  while (index < count) {
    val tokenType = type(index) ?: return null
    val tokenStart = start(index)
    when (tokenType) {
      YamlSyntaxTokenTypes.COMMENT -> Unit
      YamlSyntaxTokenTypes.EOL -> {
        lineStart = end(index)
        if (index + 1 < count && type(index + 1) !== YamlSyntaxTokenTypes.INDENT) canContinuePlainText = false
      }
      YamlSyntaxTokenTypes.INDENT -> if (tokenStart != lineStart) return null
      YamlSyntaxTokenTypes.SCALAR_KEY, YamlSyntaxTokenTypes.SEQUENCE_MARKER, YamlSyntaxTokenTypes.QUESTION -> {
        val afterIndent = index > 0 && type(index - 1) === YamlSyntaxTokenTypes.INDENT && start(index - 1) == lineStart
        val afterLinePrefix = lineStart == 0 && tokenStart == prefixLength
        val atContentStart = tokenStart == lineStart || afterIndent || afterLinePrefix
        return if (atContentStart) FirstToken(tokenType, tokenStart - lineStart, canContinuePlainText) else null
      }
      else -> return null
    }
    index++
  }
  return null
}

/**
 * Checks each line after the first line of [text]: a line with content must have at least [indent] chars of indent.
 * A line of a quoted scalar can have a smaller indent, so this check can refuse a valid text.
 */
private fun hasNoLineBelowIndent(text: CharSequence, indent: Int, insideSequence: Boolean): Boolean {
  var lineBreak = text.indexOf('\n')
  while (lineBreak >= 0) {
    var offset = lineBreak + 1
    while (offset < text.length && (text[offset] == ' ' || text[offset] == '\t')) offset++
    val char = text.getOrNull(offset)
    if (char != null && char != '\n' && char != '#') {
      val bonus = if (char == '-' && !insideSequence) 1 else 0
      if (offset - lineBreak - 1 + bonus < indent) return false
    }
    lineBreak = text.indexOf('\n', offset)
  }
  return true
}

private fun nextLineStart(text: CharSequence, offset: Int): Int {
  val lineBreak = text.indexOf('\n', offset)
  return if (lineBreak < 0) text.length else lineBreak + 1
}

/**
 * The tokens of a text, with the lexer state before each token.
 * The lexing stops at the first token at or after an end offset.
 * [endOffset] is the start of this token or the length of the text, and [endState] is the state before this token.
 */
private class LexedText(capacity: Int) {
  var starts = IntArray(capacity)
  var states = IntArray(capacity)
  var types = arrayOfNulls<SyntaxElementType>(capacity)
  var count = 0
  var endOffset = 0
  var endState = END_OF_TEXT

  fun add(start: Int, type: SyntaxElementType, state: Int) {
    if (count == starts.size) {
      val size = count * 2
      starts = starts.copyOf(size)
      states = states.copyOf(size)
      types = types.copyOf(size)
    }
    starts[count] = start
    states[count] = state
    types[count] = type
    count++
  }

  /** Returns the index of the token that starts at [offset], or -1. */
  fun indexOf(offset: Int): Int {
    val index = starts.binarySearch(offset, 0, count)
    return if (index >= 0) index else -1
  }

  /**
   * Returns the index of the token that starts at [offset], or [count] if the lexing stops at [offset].
   * Returns -1 if a token crosses [offset].
   */
  fun boundaryIndex(offset: Int): Int = if (offset == endOffset && endState != CROSSED) count else indexOf(offset)

  /**
   * Adds the indent of each line up to the first token that is not a comment, a line break or an indent.
   * Two element indents of [plainTextOn] between the same two of these indents give the same tokens.
   */
  fun addHeadLineIndents(indents: MutableSet<Int>) {
    for (index in 0 until count) {
      val type = types[index]
      if (type === YamlSyntaxTokenTypes.INDENT) indents.add((if (index + 1 < count) starts[index + 1] else endOffset) - starts[index])
      else if (type !in HEAD_TOKENS) return
    }
  }

  /** Checks that the tokens start with [tokens], and that the next token starts at the end of [tokens]. */
  fun startsWith(tokens: TokenList): Boolean {
    if (count < tokens.tokenCount) return false
    for (index in 0 until tokens.tokenCount) {
      if (starts[index] != tokens.getTokenStart(index) || types[index] !== tokens.getTokenType(index)) return false
    }
    return boundaryIndex(tokens.tokenizedText.length) == tokens.tokenCount
  }

  /** Returns the tokens from [fromIndex] up to [toIndex] for [text], which starts at the token of [fromIndex]. */
  fun toTokenList(fromIndex: Int, toIndex: Int, text: CharSequence): TokenList {
    val size = toIndex - fromIndex
    val base = starts[fromIndex]
    val lexStarts = IntArray(size + 1) { if (it < size) starts[fromIndex + it] - base else text.length }
    @Suppress("UNCHECKED_CAST")
    val lexTypes = types.copyOfRange(fromIndex, toIndex) as Array<SyntaxElementType>
    return TokenList(lexStarts, lexTypes, size, text)
  }
}

/** The [LexedText.endState] at the end of the text. */
private const val END_OF_TEXT = Int.MIN_VALUE

/** The [LexedText.endState] when a token crosses the end offset. */
private const val CROSSED = Int.MIN_VALUE + 1

/** Lexes [text] from [PLAIN_TEXT_OFF] at the offset zero, which must be a line start, up to the first token at or after [end]. */
private fun lex(text: CharSequence, end: Int, cancellationProvider: CancellationProvider?): LexedText {
  val lexed = LexedText(maxOf(16, text.length / 4))
  val lexer = YamlLexer()
  lexer.start(text, 0, text.length, PLAIN_TEXT_OFF)
  lexed.endOffset = text.length
  while (true) {
    val type = lexer.getTokenType() ?: break
    val start = lexer.getTokenStart()
    if (start >= end) {
      lexed.endOffset = start
      lexed.endState = if (start == end) lexer.getState() else CROSSED
      break
    }
    if (lexed.count and 0xff == 0) cancellationProvider?.checkCancelled()
    lexed.add(start, type, lexer.getState())
    lexer.advance()
  }
  return lexed
}

/**
 * Checks that the old and the new text have the same tokens from the end of the node up to the next line start,
 * and the same lexer state at the next line start.
 * Then the lexer gives the same tokens for the rest of the file.
 */
private fun sameTokensAndState(
  oldLexed: LexedText, oldEnd: Int, oldBoundary: Int,
  newLexed: LexedText, newEnd: Int, newBoundary: Int,
): Boolean {
  if (oldLexed.endOffset != oldBoundary || oldLexed.endState == CROSSED) return false
  var oldIndex = oldLexed.boundaryIndex(oldEnd)
  var newIndex = newLexed.boundaryIndex(newEnd)
  if (oldIndex < 0 || newIndex < 0) return false
  while (oldIndex < oldLexed.count) {
    if (newIndex >= newLexed.count) return false
    if (oldLexed.starts[oldIndex] - oldEnd != newLexed.starts[newIndex] - newEnd || oldLexed.types[oldIndex] !== newLexed.types[newIndex]) return false
    oldIndex++
    newIndex++
  }
  val newState = if (newIndex < newLexed.count && newLexed.starts[newIndex] == newBoundary) newLexed.states[newIndex]
  else if (newIndex == newLexed.count && oldLexed.endState == END_OF_TEXT) END_OF_TEXT
  else return false
  if (newState != oldLexed.endState) return false
  return newState == END_OF_TEXT || YamlLexerStates.isRestartable(newState)
}

private enum class Convergence { SAME_STATE, DIFFERENT_TOKENS, NOT_REACHED }

/**
 * Lexes [text] from [startState] and compares the tokens with [reference], which is lexed from [PLAIN_TEXT_OFF] up to [end].
 * Returns [Convergence.SAME_STATE] when both lexings reach a line start with the same restartable state at or before [end].
 * After this line start, both lexings give the same tokens.
 * It also returns [Convergence.SAME_STATE] when both lexings give the same tokens up to the end of the text.
 * A line with a small indent turns the plain text flag off after its indent.
 * So the states are the same only at the next line start.
 */
private fun lexUntilSameState(text: CharSequence, reference: LexedText, end: Int, startState: Int, cancellationProvider: CancellationProvider?): Convergence {
  val lexer = YamlLexer()
  lexer.start(text, 0, text.length, startState)
  var index = 0
  while (true) {
    val type = lexer.getTokenType()
    if (type == null) {
      return if (index == reference.count && reference.endOffset == text.length) Convergence.SAME_STATE else Convergence.NOT_REACHED
    }
    val start = lexer.getTokenStart()
    if (start > end) return Convergence.NOT_REACHED
    val state = lexer.getState()
    val referenceState = when {
      index < reference.count -> {
        if (start != reference.starts[index] || type !== reference.types[index]) return Convergence.DIFFERENT_TOKENS
        reference.states[index]
      }
      start == reference.endOffset -> reference.endState
      else -> return Convergence.NOT_REACHED
    }
    if (index > 0 && text[start - 1] == '\n' && YamlLexerStates.isRestartable(state) && state == referenceState) {
      return Convergence.SAME_STATE
    }
    if (start == end) return Convergence.NOT_REACHED
    if (index and 0xff == 0) cancellationProvider?.checkCancelled()
    lexer.advance()
    index++
  }
}

/**
 * Parses the new text with the tokens after it, as a full parse of the file sees them.
 * Checks that the parser reads one node of [nodeType] that ends at the end of the new text.
 * Returns the production with a root marker, then the marker of the node, or null.
 */
private fun parseWithTail(
  buffer: CharSequence,
  lexed: LexedText,
  firstIndex: Int,
  prefixLength: Int,
  textLength: Int,
  nodeType: SyntaxElementType,
  indent: Int,
  insideSequence: Boolean,
  cancellationProvider: CancellationProvider?,
): ProductionResult? {
  val text = buffer.subSequence(prefixLength, buffer.length)
  val tokens = lexed.toTokenList(firstIndex, lexed.count, text)
  val builder = SyntaxTreeBuilderFactory.builder(text, tokens, YamlSyntaxDefinition.whitespaces, YamlSyntaxDefinition.comments)
    .withCancellationProvider(cancellationProvider)
    .build()
  val root = builder.mark()
  val node = builder.mark()
  val parser = YamlParser(builder)
  val parsedType = parser.parseBlockCollection(prefixLength, indent, insideSequence)
  if (parsedType !== nodeType || builder.currentOffset != textLength || parser.statementInLineOfPreviousSeen) return null
  node.done(nodeType)
  while (!builder.eof()) {
    builder.advanceLexer()
  }
  root.done(nodeType)
  return prepareProduction(builder)
}

/**
 * Checks that the markers inside the node of [alone] are the same as the markers inside the node of [withTail].
 * [alone] has the node as its root, and [withTail] has the node as the only child of its root.
 * A parse of the node tokens alone ends at the end of the text.
 * There the parser can put a last line break, indent or comment into another node than a full parse does.
 */
private fun sameInnerMarkers(alone: ProductionMarkerList, withTail: ProductionMarkerList): Boolean {
  if (withTail.size != alone.size + 2) return false
  for (index in 1 until alone.size - 1) {
    val done = alone.isDoneMarker(index)
    if (done != withTail.isDoneMarker(index + 1)) return false
    val expected = alone.getMarker(index)
    val actual = withTail.getMarker(index + 1)
    if (expected.getNodeType() !== actual.getNodeType() ||
        expected.getStartOffset() != actual.getStartOffset() ||
        expected.getEndOffset() != actual.getEndOffset() ||
        expected.getErrorMessage() != actual.getErrorMessage()) {
      return false
    }
  }
  return true
}
