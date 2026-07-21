package org.jetbrains.yaml.syntax

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesAndCommentsBinder
import com.intellij.platform.syntax.parser.WhitespacesBinders
import com.intellij.platform.syntax.syntaxElementTypeSetOf

class YamlParser(private val builder: SyntaxTreeBuilder) {
    private var wasEolSeen = false
    private var currentIndent = 0
    private var markerAfterLastEol: SyntaxTreeBuilder.Marker? = null

    private val stopTokensStack = ArrayDeque<SyntaxElementTypeSet>()

    fun parse() {
        val fileMarker = mark()
        // stopTokensStack.clear()
        parseFile()
        // require(builder.eof()) { "Not all tokens were passed." }
        fileMarker.done(YamlSyntaxElementTypes.FILE)
    }

    private fun parseFile() {
        val marker = mark()
        passJunk()
        if (builder.tokenType != YamlSyntaxTokenTypes.DOCUMENT_MARKER) {
            dropEolMarker()
            marker.rollbackTo()
        } else {
            marker.drop()
        }
        do {
            parseDocument()
            passJunk()
        } while (!builder.eof())
        dropEolMarker()
    }

    private fun parseDocument() {
        val marker = mark()
        if (builder.tokenType == YamlSyntaxTokenTypes.DOCUMENT_MARKER) {
            advanceLexer()
        }
        parseBlockNode(currentIndent, insideSequence = false)
        dropEolMarker()
        marker.done(YamlSyntaxElementTypes.DOCUMENT)
    }

    private fun parseBlockNode(indent: Int, insideSequence: Boolean) {
        // Preserve most test and current behaviour for most general cases without comments
        if (getTokenType() === YamlSyntaxTokenTypes.EOL) {
            advanceLexer()
            if (getTokenType() === YamlSyntaxTokenTypes.INDENT) {
                advanceLexer()
            }
        }

        val marker = mark()
        passJunk()

        var endOfNodeMarker: SyntaxTreeBuilder.Marker? = null
        var nodeType: SyntaxElementType? = null


        // It looks like tag for a block node should be located on a separate line
        if (getTokenType() === YamlSyntaxTokenTypes.TAG
            && builder.lookAhead(1) === YamlSyntaxTokenTypes.EOL
        ) {
            advanceLexer()
        }

        var numberOfItems = 0
        while (
            !eof() && (isJunk() || !wasEolSeen || currentIndent + getIndentBonus(insideSequence) >= indent)
        ) {
            if (isJunk()) {
                advanceLexer()
                continue
            }

            if (!stopTokensStack.isEmpty() && stopTokensStack.last().contains(getTokenType())) {
                rollBackToEol()
                break
            }

            numberOfItems++
            val parsedTokenType = parseSingleStatement(
                if (wasEolSeen) currentIndent else indent,
                minIndent = indent
            )
            if (nodeType == null) {
                if (parsedTokenType === YamlSyntaxElementTypes.SEQUENCE_ITEM) {
                    nodeType = YamlSyntaxElementTypes.SEQUENCE
                } else if (parsedTokenType === YamlSyntaxElementTypes.KEY_VALUE_PAIR) {
                    nodeType = YamlSyntaxElementTypes.MAPPING
                } else if (numberOfItems > 1) {
                    nodeType = YamlSyntaxElementTypes.COMPOUND_VALUE
                }
            }
            endOfNodeMarker?.drop()
            endOfNodeMarker = mark()
        }

        if (endOfNodeMarker != null) {
            dropEolMarker()
            endOfNodeMarker.rollbackTo()
        } else {
            rollBackToEol()
        }

        includeBlockEmptyTail(indent)

        if (nodeType != null) {
            marker.done(nodeType)
            marker.setCustomEdgeTokenBinders(
                left = object : WhitespacesAndCommentsBinder {
                    override fun getEdgePosition(
                        tokens: List<SyntaxElementType>,
                        atStreamEdge: Boolean,
                        getter: WhitespacesAndCommentsBinder.TokenTextGetter
                    ): Int = findLeftRange(tokens)
                },
                WhitespacesBinders.greedyRightBinder(),
            )
        } else {
            marker.drop()
        }
    }

    private fun includeBlockEmptyTail(indent: Int) {
        if (indent == 0) {
            // top-level block with zero indent
            while (isJunk()) {
                if (getTokenType() === YamlSyntaxTokenTypes.EOL) {
                    if (!YamlSyntaxElementTypes.BLANK_ELEMENTS.contains(builder.lookAhead(1))) {
                        // do not include last \n into block
                        break
                    }
                }
                advanceLexer()
                dropEolMarker()
            }
        } else {
            var endOfBlock: SyntaxTreeBuilder.Marker = mark()
            while (isJunk()) {
                if (getTokenType() === YamlSyntaxTokenTypes.INDENT && getCurrentTokenLength() >= indent) {
                    dropEolMarker()
                    endOfBlock.drop()
                    advanceLexer()
                    endOfBlock = mark()
                } else {
                    advanceLexer()
                    dropEolMarker()
                }
            }
            endOfBlock.rollbackTo()
        }
    }

    /**
     * @link {http://www.yaml.org/spec/1.2/spec.html#id2777534}
     */
    private fun getIndentBonus(insideSequence: Boolean): Int {
        return if (!insideSequence && getTokenType() === YamlSyntaxTokenTypes.SEQUENCE_MARKER) 1 else 0
    }

    private fun getShorthandIndentAddition(): Int {
        val offset = builder.currentOffset
        val nextToken = builder.lookAhead(1)
        if (nextToken !== YamlSyntaxTokenTypes.SEQUENCE_MARKER && nextToken !== YamlSyntaxTokenTypes.SCALAR_KEY) {
            return 1
        }
        if (builder.rawLookup(1) === YamlSyntaxTokenTypes.WHITESPACE) {
            return builder.rawTokenTypeStart(2) - offset
        } else {
            return 1
        }
    }

    private fun parseSingleStatement(indent: Int, minIndent: Int): SyntaxElementType? {
        if (eof()) {
            return null
        }

        val marker = mark()
        parseNodeProperties()

        val tokenType = getTokenType()
        val nodeType: SyntaxElementType?
        if (tokenType === YamlSyntaxTokenTypes.LBRACE) {
            nodeType = parseHash()
        } else if (tokenType === YamlSyntaxTokenTypes.LBRACKET) {
            nodeType = parseFlowCollectionKeyValue(indent, ::parseArray)
        } else if (tokenType === YamlSyntaxTokenTypes.SEQUENCE_MARKER) {
            nodeType = parseSequenceItem(indent)
        } else if (tokenType === YamlSyntaxTokenTypes.QUESTION) {
            nodeType = parseExplicitKeyValue(indent)
        } else if (tokenType === YamlSyntaxTokenTypes.SCALAR_KEY) {
            nodeType = parseScalarKeyValue(indent)
        } else if (YamlSyntaxElementTypes.SCALAR_VALUES.contains(tokenType)) {
            nodeType = parseScalarValue(minIndent)
        } else if (tokenType === YamlSyntaxTokenTypes.STAR) {
            val aliasMarker = mark()
            advanceLexer() // symbol *
            if (getTokenType() === YamlSyntaxTokenTypes.ALIAS) {
                advanceLexer() // alias name
                aliasMarker.done(YamlSyntaxElementTypes.ALIAS_NODE)
                if (getTokenType() === YamlSyntaxTokenTypes.COLON) {
                    // Alias is used as key name
                    wasEolSeen = false
                    val indentAddition = getShorthandIndentAddition()
                    nodeType = parseSimpleScalarKeyValueFromColon(indent, indentAddition)
                } else {
                    // simple ALIAS_NODE was constructed and marker should be dropped
                    marker.drop()
                    return YamlSyntaxElementTypes.ALIAS_NODE
                }
            } else {
                // Should be impossible now (because of lexer rules)
                aliasMarker.drop()
                nodeType = null
            }
        } else {
            advanceLexer()
            nodeType = null
        }

        if (nodeType != null) {
            marker.done(nodeType)
        } else {
            marker.drop()
        }
        return nodeType
    }

    private fun parseFlowCollectionKeyValue(
        indent: Int,
        parseCollection: () -> SyntaxElementType,
    ): SyntaxElementType {
        val collectionMarker = mark()
        val collectionType = parseCollection()
        if (getTokenType() === YamlSyntaxTokenTypes.COLON
            && (stopTokensStack.isEmpty() || stopTokensStack.first().contains(YamlSyntaxTokenTypes.COLON))
        ) {
            collectionMarker.done(collectionType)
            wasEolSeen = false
            val indentAddition = getShorthandIndentAddition()
            return parseSimpleScalarKeyValueFromColon(indent, indentAddition)
        }
        collectionMarker.drop()
        return collectionType
    }

    /**
     * Each node may have two optional properties, anchor and tag, in addition to its content.
     * Node properties may be specified in any order before the node’s content.
     * Either or both may be omitted.
     *
     * <pre>
     * [96] c-ns-properties(n,c) ::= ( c-ns-tag-property ( s-separate(n,c) c-ns-anchor-property )? )
     * | ( c-ns-anchor-property ( s-separate(n,c) c-ns-tag-property )? )
     *
    </pre> *
     * See [6.9. Node Properties](http://www.yaml.org/spec/1.2/spec.html#id2783797)
     */
    private fun parseNodeProperties() {
        // By standard here could be no more than one TAG or ANCHOR
        // By better to support sequence of them
        var anchorWasRead = false
        var tagWasRead = false
        while (getTokenType() === YamlSyntaxTokenTypes.TAG || getTokenType() === YamlSyntaxTokenTypes.AMPERSAND) {
            if (getTokenType() === YamlSyntaxTokenTypes.AMPERSAND) {
                var errorMarker: SyntaxTreeBuilder.Marker? = null
                if (anchorWasRead) {
                    errorMarker = mark()
                }
                anchorWasRead = true
                val anchorMarker = mark()
                advanceLexer() // symbol &
                if (getTokenType() === YamlSyntaxTokenTypes.ANCHOR) {
                    advanceLexer() // anchor name
                    anchorMarker.done(YamlSyntaxElementTypes.ANCHOR_NODE)
                } else {
                    // Should be impossible now (because of lexer rules)
                    anchorMarker.drop()
                }
                errorMarker?.error(YamlSyntaxBundle.message("YAMLParser.multiple.anchors"))
            } else { // tag case
                if (tagWasRead) {
                    val errorMarker = mark()
                    advanceLexer()
                    errorMarker.error(YamlSyntaxBundle.message("YAMLParser.multiple.tags"))
                } else {
                    tagWasRead = true
                    advanceLexer()
                }
            }
        }
    }

    private fun parseScalarValue(indent: Int): SyntaxElementType? {
        val tokenType = getTokenType()
//        require(YamlSyntaxElementTypes.SCALAR_VALUES.contains(tokenType)) { "Scalar value expected!" }
        if (tokenType === YamlSyntaxTokenTypes.SCALAR_LIST || tokenType === YamlSyntaxTokenTypes.SCALAR_TEXT) {
            return parseMultiLineScalar(tokenType)
        } else if (tokenType === YamlSyntaxTokenTypes.TEXT) {
            return parseMultiLinePlainScalar(indent)
        } else if (tokenType === YamlSyntaxTokenTypes.SCALAR_DSTRING || tokenType === YamlSyntaxTokenTypes.SCALAR_STRING) {
            return parseQuotedString()
        } else {
            advanceLexer()
            return null
        }
    }

    private fun parseQuotedString(): SyntaxElementType {
        advanceLexer()
        return YamlSyntaxElementTypes.SCALAR_QUOTED_STRING
    }

    private fun parseMultiLineScalar(tokenType: SyntaxElementType?): SyntaxElementType {
//        require(tokenType === getTokenType())
        // Accept header token: '|' or '>'
        advanceLexer()

        // Parse header tail: TEXT is used as placeholder for invalid symbols in this context
        if (getTokenType() === YamlSyntaxTokenTypes.TEXT) {
            val err = builder.mark()
            advanceLexer()
            err.error(YamlSyntaxBundle.message("YAMLParser.invalid.header.symbols"))
        }

        if (YamlSyntaxElementTypes.EOL_ELEMENTS.contains(getTokenType())) {
            advanceLexer()
        }
        var endOfValue: SyntaxTreeBuilder.Marker? = builder.mark()

        var type: SyntaxElementType? = getTokenType()
        // Lexer ensures such input token structure: ( ( INDENT tokenType? )? SCALAR_EOL )*
        // endOfValue marker is needed to exclude INDENT after last SCALAR_EOL
        while (type === tokenType || type === YamlSyntaxTokenTypes.INDENT || type === YamlSyntaxTokenTypes.SCALAR_EOL) {
            advanceLexer()
            if (type === tokenType) {
                endOfValue?.drop()
                endOfValue = null
            }
            if (type === YamlSyntaxTokenTypes.SCALAR_EOL) {
                if (endOfValue != null) {
                    endOfValue.drop()
                }
                endOfValue = builder.mark()
            }

            type = getTokenType()
        }
        endOfValue?.rollbackTo()

        return if (tokenType === YamlSyntaxTokenTypes.SCALAR_LIST) YamlSyntaxElementTypes.SCALAR_LIST_VALUE else YamlSyntaxElementTypes.SCALAR_TEXT_VALUE
    }

    private fun parseMultiLinePlainScalar(indent: Int): SyntaxElementType {
        var lastTextEnd: SyntaxTreeBuilder.Marker? = null

        var type: SyntaxElementType? = getTokenType()
        while (type === YamlSyntaxTokenTypes.TEXT || type === YamlSyntaxTokenTypes.INDENT || type === YamlSyntaxTokenTypes.EOL) {
            advanceLexer()

            if (type === YamlSyntaxTokenTypes.TEXT) {
                if (lastTextEnd != null && currentIndent < indent) {
                    break
                }
                lastTextEnd?.drop()
                lastTextEnd = mark()
            }
            type = getTokenType()
        }

        rollBackToEol()
        checkNotNull(lastTextEnd).rollbackTo()
        return YamlSyntaxElementTypes.SCALAR_PLAIN_VALUE
    }

    private fun parseExplicitKeyValue(indent: Int): SyntaxElementType {
//        require(getTokenType() === YamlSyntaxTokenTypes.QUESTION)

        var indentAddition = getShorthandIndentAddition()
        advanceLexer()

        if (!stopTokensStack.isEmpty() && stopTokensStack.last() == HASH_STOP_TOKENS // This means we're inside some hash
            && getTokenType() === YamlSyntaxTokenTypes.SCALAR_KEY
        ) {
            parseScalarKeyValue(indent)
        } else {
            stopTokensStack.add(syntaxElementTypeSetOf(YamlSyntaxTokenTypes.COLON))
            wasEolSeen = false

            parseBlockNode(indent + indentAddition, false)

            stopTokensStack.removeLast()

            passJunk()
            if (getTokenType() === YamlSyntaxTokenTypes.COLON) {
                indentAddition = getShorthandIndentAddition()
                advanceLexer()

                wasEolSeen = false
                parseBlockNode(indent + indentAddition, false)
            }
        }

        return YamlSyntaxElementTypes.KEY_VALUE_PAIR
    }


    private fun parseScalarKeyValue(indent: Int): SyntaxElementType {
//        require(getTokenType() === YamlSyntaxTokenTypes.SCALAR_KEY) { "Expected scalar key" }
        wasEolSeen = false

        val indentAddition = getShorthandIndentAddition()
        advanceLexer()

        return parseSimpleScalarKeyValueFromColon(indent, indentAddition)
    }

    private fun parseSimpleScalarKeyValueFromColon(indent: Int, indentAddition: Int): SyntaxElementType {
//        require(getTokenType() === YamlSyntaxTokenTypes.COLON) { "Expected colon" }
        advanceLexer()

        val rollbackMarker = mark()

        passJunk()
        if (wasEolSeen && (eof() || currentIndent + getIndentBonus(false) < indent + indentAddition)) {
            dropEolMarker()
            rollbackMarker.rollbackTo()
        } else {
            dropEolMarker()
            rollbackMarker.rollbackTo()
            parseBlockNode(indent + indentAddition, false)
        }

        return YamlSyntaxElementTypes.KEY_VALUE_PAIR
    }

    private fun parseSequenceItem(indent: Int): SyntaxElementType {
//        require(getTokenType() === YamlSyntaxTokenTypes.SEQUENCE_MARKER)

        val indentAddition = getShorthandIndentAddition()
        advanceLexer()
        wasEolSeen = false

        parseBlockNode(indent + indentAddition, true)
        rollBackToEol()
        return YamlSyntaxElementTypes.SEQUENCE_ITEM
    }

    private fun parseHash(): SyntaxElementType {
//        require(getTokenType() === YamlSyntaxTokenTypes.LBRACE)
        advanceLexer()
        stopTokensStack.add(HASH_STOP_TOKENS)

        while (!eof()) {
            if (getTokenType() === YamlSyntaxTokenTypes.RBRACE) {
                advanceLexer()
                break
            }
            parseSingleStatement(0, 0)
        }

        stopTokensStack.removeLast()
        dropEolMarker()
        return YamlSyntaxElementTypes.HASH
    }

    private fun parseArray(): SyntaxElementType {
//        require(getTokenType() === YamlSyntaxTokenTypes.LBRACKET)
        advanceLexer()
        stopTokensStack.add(ARRAY_STOP_TOKENS)

        while (!eof()) {
            if (getTokenType() === YamlSyntaxTokenTypes.RBRACKET) {
                advanceLexer()
                break
            }
            if (isJunk()) {
                advanceLexer()
                continue
            }

            val marker = mark()
            val parsedElement = parseSingleStatement(0, 0)
            if (parsedElement != null) {
                marker.done(YamlSyntaxElementTypes.SEQUENCE_ITEM)
            } else {
                marker.error(YamlSyntaxBundle.message("parsing.error.sequence.item.expected"))
            }

            if (getTokenType() === YamlSyntaxTokenTypes.COMMA) {
                advanceLexer()
            }
        }

        stopTokensStack.removeLast()
        dropEolMarker()
        return YamlSyntaxElementTypes.ARRAY
    }

    private fun eof(): Boolean {
        return builder.eof() || builder.tokenType === YamlSyntaxTokenTypes.DOCUMENT_MARKER
    }

    private fun getTokenType(): SyntaxElementType? {
        return if (eof()) null else builder.tokenType
    }

    private fun dropEolMarker() {
        markerAfterLastEol?.drop()
        markerAfterLastEol = null
    }

    private fun rollBackToEol() {
        if (wasEolSeen && markerAfterLastEol != null) {
            wasEolSeen = false
            markerAfterLastEol!!.rollbackTo()
            markerAfterLastEol = null
        }
    }

    private fun mark(): SyntaxTreeBuilder.Marker {
        dropEolMarker()
        return builder.mark()
    }

    private fun advanceLexer() {
        if (builder.eof()) {
            return
        }
        val type = builder.tokenType
        val eolElement: Boolean = YamlSyntaxElementTypes.EOL_ELEMENTS.contains(type)
        wasEolSeen = wasEolSeen || eolElement
        if (eolElement) {
            // Drop and create new eolMarker
            markerAfterLastEol = mark()
            currentIndent = 0
        } else if (type === YamlSyntaxTokenTypes.INDENT) {
            currentIndent = getCurrentTokenLength()
        } else {
            // Drop Eol Marker if other token seen
            dropEolMarker()
        }
        builder.advanceLexer()
    }

    private fun getCurrentTokenLength(): Int {
        return builder.rawTokenTypeStart(1) - builder.currentOffset
    }

    private fun passJunk() {
        while (!eof() && isJunk()) {
            advanceLexer()
        }
    }

    private fun isJunk(): Boolean {
        val type = getTokenType()
        return type === YamlSyntaxTokenTypes.INDENT || type === YamlSyntaxTokenTypes.EOL
    }

    companion object {
        private val HASH_STOP_TOKENS = syntaxElementTypeSetOf(
            YamlSyntaxTokenTypes.RBRACE,
            YamlSyntaxTokenTypes.COMMA,
        )
        private val ARRAY_STOP_TOKENS = syntaxElementTypeSetOf(
            YamlSyntaxTokenTypes.RBRACKET,
            YamlSyntaxTokenTypes.COMMA,
        )

        private fun findLeftRange(tokens: List<SyntaxElementType>): Int {
            val index = tokens.indexOf(YamlSyntaxTokenTypes.COMMENT)
            return if (index == -1) tokens.size else index
        }
    }
}