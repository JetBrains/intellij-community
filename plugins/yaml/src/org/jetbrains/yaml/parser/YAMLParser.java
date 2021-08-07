// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;

import java.util.List;

public class YAMLParser implements PsiParser, LightPsiParser, YAMLTokenTypes {
  public static final TokenSet HASH_STOP_TOKENS = TokenSet.create(RBRACE, COMMA);
  public static final TokenSet ARRAY_STOP_TOKENS = TokenSet.create(RBRACKET, COMMA);
  private PsiBuilder myBuilder;
  private boolean eolSeen = false;
  private int myIndent;
  private PsiBuilder.Marker myAfterLastEolMarker;

  private final Stack<TokenSet> myStopTokensStack = new Stack<>();

  @Override
  @NotNull
  public ASTNode parse(@NotNull final IElementType root, @NotNull final PsiBuilder builder) {
    parseLight(root, builder);
    return builder.getTreeBuilt();
  }


  @Override
  public void parseLight(IElementType root, PsiBuilder builder) {
    myBuilder = builder;
    myStopTokensStack.clear();
    final PsiBuilder.Marker fileMarker = mark();
    parseFile();
    assert myBuilder.eof() : "Not all tokens were passed.";
    fileMarker.done(root);
  }

  private void parseFile() {
    PsiBuilder.Marker marker = mark();
    passJunk();
    if (myBuilder.getTokenType() != DOCUMENT_MARKER) {
      dropEolMarker();
      marker.rollbackTo();
    }
    else {
      marker.drop();
    }
    do {
      parseDocument();
      passJunk();
    } while (!myBuilder.eof());
    dropEolMarker();
  }

  private void parseDocument() {
    final PsiBuilder.Marker marker = mark();
    if (myBuilder.getTokenType() == DOCUMENT_MARKER) {
      advanceLexer();
    }
    parseBlockNode(myIndent, false);
    dropEolMarker();
    marker.done(YAMLElementTypes.DOCUMENT);
  }

  private void parseBlockNode(int indent, boolean insideSequence) {
    // Preserve most test and current behaviour for most general cases without comments
    if (getTokenType() == EOL) {
      advanceLexer();
      if (getTokenType() == INDENT) {
        advanceLexer();
      }
    }

    final PsiBuilder.Marker marker = mark();
    passJunk();

    PsiBuilder.Marker endOfNodeMarker = null;
    IElementType nodeType = null;


    // It looks like tag for a block node should be located on a separate line
    if (getTokenType() == YAMLTokenTypes.TAG && myBuilder.lookAhead(1) == YAMLTokenTypes.EOL) {
      advanceLexer();
    }

    int numberOfItems = 0;
    while (!eof() && (isJunk() || !eolSeen || myIndent + getIndentBonus(insideSequence) >= indent)) {
      if (isJunk()) {
        advanceLexer();
        continue;
      }

      if (!myStopTokensStack.isEmpty() && myStopTokensStack.peek().contains(getTokenType())) {
        rollBackToEol();
        break;
      }

      numberOfItems++;
      final IElementType parsedTokenType = parseSingleStatement(eolSeen ? myIndent : indent, indent);
      if (nodeType == null) {
        if (parsedTokenType == YAMLElementTypes.SEQUENCE_ITEM) {
          nodeType = YAMLElementTypes.SEQUENCE;
        }
        else if (parsedTokenType == YAMLElementTypes.KEY_VALUE_PAIR) {
          nodeType = YAMLElementTypes.MAPPING;
        }
        else if (numberOfItems > 1) {
          nodeType = YAMLElementTypes.COMPOUND_VALUE;
        }
      }
      if (endOfNodeMarker != null) {
        endOfNodeMarker.drop();
      }
      endOfNodeMarker = mark();

    }

    if (endOfNodeMarker != null) {
      dropEolMarker();
      endOfNodeMarker.rollbackTo();
    }
    else {
      rollBackToEol();
    }

    includeBlockEmptyTail(indent);

    if (nodeType != null) {
      marker.done(nodeType);
      marker.setCustomEdgeTokenBinders(
        (tokens, atStreamEdge, getter) -> findLeftRange(tokens),
        (tokens, atStreamEdge, getter) -> tokens.size());
    }
    else {
      marker.drop();
    }
  }

  private void includeBlockEmptyTail(int indent) {
    if (indent == 0) {
      // top-level block with zero indent
      while (isJunk()) {
        if (getTokenType() == EOL) {
          if (!YAMLElementTypes.BLANK_ELEMENTS.contains(myBuilder.lookAhead(1))) {
            // do not include last \n into block
            break;
          }
        }
        advanceLexer();
        dropEolMarker();
      }
    }
    else {
      PsiBuilder.Marker endOfBlock = mark();
      while (isJunk()) {
        if (getTokenType() == INDENT && getCurrentTokenLength() >= indent) {
            dropEolMarker();
            endOfBlock.drop();
            advanceLexer();
            endOfBlock = mark();
        } else {
          advanceLexer();
          dropEolMarker();
        }
      }
      endOfBlock.rollbackTo();
    }
  }

  /**
   * @link {http://www.yaml.org/spec/1.2/spec.html#id2777534}
   */
  private int getIndentBonus(final boolean insideSequence) {
    if (!insideSequence && getTokenType() == SEQUENCE_MARKER) {
      return 1;
    }
    else {
      return 0;
    }
  }

  private int getShorthandIndentAddition() {
    final int offset = myBuilder.getCurrentOffset();
    final IElementType nextToken = myBuilder.lookAhead(1);
    if (nextToken != SEQUENCE_MARKER && nextToken != SCALAR_KEY) {
      return 1;
    }
    if (myBuilder.rawLookup(1) == WHITESPACE) {
      return myBuilder.rawTokenTypeStart(2) - offset;
    }
    else {
      return 1;
    }
  }

  @Nullable
  private IElementType parseSingleStatement(int indent, int minIndent) {
    if (eof()) {
      return null;
    }

    final PsiBuilder.Marker marker = mark();
    parseNodeProperties();

    final IElementType tokenType = getTokenType();
    final IElementType nodeType;
    if (tokenType == LBRACE) {
      nodeType = parseHash();
    }
    else if (tokenType == LBRACKET) {
      nodeType = parseArray();
    }
    else if (tokenType == SEQUENCE_MARKER) {
      nodeType = parseSequenceItem(indent);
    }
    else if (tokenType == QUESTION) {
      nodeType = parseExplicitKeyValue(indent);
    }
    else if (tokenType == SCALAR_KEY) {
      nodeType = parseScalarKeyValue(indent);
    }
    else if (YAMLElementTypes.SCALAR_VALUES.contains(getTokenType())) {
      nodeType = parseScalarValue(minIndent);
    }
    else if (tokenType == STAR) {
      PsiBuilder.Marker aliasMarker = mark();
      advanceLexer(); // symbol *
      if (getTokenType() == YAMLTokenTypes.ALIAS) {
        advanceLexer(); // alias name
        aliasMarker.done(YAMLElementTypes.ALIAS_NODE);
        if (getTokenType() == COLON) {
          // Alias is used as key name
          eolSeen = false;
          int indentAddition = getShorthandIndentAddition();
          nodeType = parseSimpleScalarKeyValueFromColon(indent, indentAddition);
        }
        else {
          // simple ALIAS_NODE was constructed and marker should be dropped
          marker.drop();
          return YAMLElementTypes.ALIAS_NODE;
        }
      }
      else {
        // Should be impossible now (because of lexer rules)
        aliasMarker.drop();
        nodeType = null;
      }
    }
    else {
      advanceLexer();
      nodeType = null;
    }

    if (nodeType != null) {
      marker.done(nodeType);
    }
    else {
      marker.drop();
    }
    return nodeType;
  }

  /**
   * Each node may have two optional properties, anchor and tag, in addition to its content.
   * Node properties may be specified in any order before the node’s content.
   * Either or both may be omitted.
   *
   * <pre>
   * [96] c-ns-properties(n,c) ::= ( c-ns-tag-property ( s-separate(n,c) c-ns-anchor-property )? )
   *                             | ( c-ns-anchor-property ( s-separate(n,c) c-ns-tag-property )? )
   *
   * </pre>
   * See <a href="http://www.yaml.org/spec/1.2/spec.html#id2783797">6.9. Node Properties</a>
   */
  private void parseNodeProperties() {
    // By standard here could be no more than one TAG or ANCHOR
    // By better to support sequence of them
    boolean anchorWasRead = false;
    boolean tagWasRead = false;
    while (getTokenType() == YAMLTokenTypes.TAG || getTokenType() == YAMLTokenTypes.AMPERSAND) {
      if (getTokenType() == YAMLTokenTypes.AMPERSAND) {
        PsiBuilder.Marker errorMarker = null;
        if (anchorWasRead) {
          errorMarker = mark();
        }
        anchorWasRead = true;
        PsiBuilder.Marker anchorMarker = mark();
        advanceLexer(); // symbol &
        if (getTokenType() == YAMLTokenTypes.ANCHOR) {
          advanceLexer(); // anchor name
          anchorMarker.done(YAMLElementTypes.ANCHOR_NODE);
        }
        else {
          // Should be impossible now (because of lexer rules)
          anchorMarker.drop();
        }
        if (errorMarker != null) {
          errorMarker.error(YAMLBundle.message("YAMLParser.multiple.anchors"));
        }
      } else { // tag case
        if (tagWasRead) {
          PsiBuilder.Marker errorMarker = mark();
          advanceLexer();
          errorMarker.error(YAMLBundle.message("YAMLParser.multiple.tags"));
        }
        else {
          tagWasRead = true;
          advanceLexer();
        }
      }
    }
  }

  @Nullable
  private IElementType parseScalarValue(int indent) {
    final IElementType tokenType = getTokenType();
    assert YAMLElementTypes.SCALAR_VALUES.contains(tokenType) : "Scalar value expected!";
    if (tokenType == SCALAR_LIST || tokenType == SCALAR_TEXT) {
      return parseMultiLineScalar(tokenType);
    }
    else if (tokenType == TEXT) {
      return parseMultiLinePlainScalar(indent);
    }
    else if (tokenType == SCALAR_DSTRING || tokenType == SCALAR_STRING) {
      return parseQuotedString();
    }
    else {
      advanceLexer();
      return null;
    }
  }

  @NotNull
  private IElementType parseQuotedString() {
    advanceLexer();
    return YAMLElementTypes.SCALAR_QUOTED_STRING;
  }

  @NotNull
  private IElementType parseMultiLineScalar(final IElementType tokenType) {
    assert tokenType == getTokenType();
    // Accept header token: '|' or '>'
    advanceLexer();

    // Parse header tail: TEXT is used as placeholder for invalid symbols in this context
    if (getTokenType() == TEXT) {
      PsiBuilder.Marker err = myBuilder.mark();
      advanceLexer();
      err.error(YAMLBundle.message("YAMLParser.invalid.header.symbols"));
    }

    if (YAMLElementTypes.EOL_ELEMENTS.contains(getTokenType())) {
      advanceLexer();
    }
    PsiBuilder.Marker endOfValue = myBuilder.mark();

    IElementType type = getTokenType();
    // Lexer ensures such input token structure: ( ( INDENT tokenType? )? SCALAR_EOL )*
    // endOfValue marker is needed to exclude INDENT after last SCALAR_EOL
    while (type == tokenType || type == INDENT || type == SCALAR_EOL) {
      advanceLexer();
      if (type == tokenType) {
        if (endOfValue != null) {
          // this 'if' should be always true because of input token structure
          endOfValue.drop();
        }
        endOfValue = null;
      }
      if (type == SCALAR_EOL) {
        if (endOfValue != null) {
          endOfValue.drop();
        }
        endOfValue = myBuilder.mark();
      }

      type = getTokenType();
    }
    if (endOfValue != null) {
      endOfValue.rollbackTo();
    }

    return tokenType == SCALAR_LIST ? YAMLElementTypes.SCALAR_LIST_VALUE : YAMLElementTypes.SCALAR_TEXT_VALUE;
  }

  @NotNull
  private IElementType parseMultiLinePlainScalar(final int indent) {
    PsiBuilder.Marker lastTextEnd = null;

    IElementType type = getTokenType();
    while (type == TEXT || type == INDENT || type == EOL) {
      advanceLexer();

      if (type == TEXT) {
        if (lastTextEnd != null && myIndent < indent) {
          break;
        }
        if (lastTextEnd != null) {
          lastTextEnd.drop();
        }
        lastTextEnd = mark();
      }
      type = getTokenType();
    }

    rollBackToEol();
    assert lastTextEnd != null;
    lastTextEnd.rollbackTo();
    return YAMLElementTypes.SCALAR_PLAIN_VALUE;
  }

  @NotNull
  private IElementType parseExplicitKeyValue(int indent) {
    assert getTokenType() == QUESTION;

    int indentAddition = getShorthandIndentAddition();
    advanceLexer();

    if (!myStopTokensStack.isEmpty() && myStopTokensStack.peek() == HASH_STOP_TOKENS // This means we're inside some hash
        && getTokenType() == SCALAR_KEY) {
      parseScalarKeyValue(indent);
    }
    else {
      myStopTokensStack.add(TokenSet.create(COLON));
      eolSeen = false;

      parseBlockNode(indent + indentAddition, false);

      myStopTokensStack.pop();

      passJunk();
      if (getTokenType() == COLON) {
        indentAddition = getShorthandIndentAddition();
        advanceLexer();

        eolSeen = false;
        parseBlockNode(indent + indentAddition, false);
      }
    }

    return YAMLElementTypes.KEY_VALUE_PAIR;
  }


  @NotNull
  private IElementType parseScalarKeyValue(int indent) {
    assert getTokenType() == SCALAR_KEY : "Expected scalar key";
    eolSeen = false;

    int indentAddition = getShorthandIndentAddition();
    advanceLexer();

    return parseSimpleScalarKeyValueFromColon(indent, indentAddition);
  }

  @NotNull
  private IElementType parseSimpleScalarKeyValueFromColon(int indent, int indentAddition) {
    assert getTokenType() == COLON : "Expected colon";
    advanceLexer();

    final PsiBuilder.Marker rollbackMarker = mark();

    passJunk();
    if (eolSeen && (eof() || myIndent + getIndentBonus(false) < indent + indentAddition)) {
      dropEolMarker();
      rollbackMarker.rollbackTo();
    }
    else {
      dropEolMarker();
      rollbackMarker.rollbackTo();
      parseBlockNode(indent + indentAddition, false);
    }

    return YAMLElementTypes.KEY_VALUE_PAIR;
  }

  @NotNull
  private IElementType parseSequenceItem(int indent) {
    assert getTokenType() == SEQUENCE_MARKER;

    int indentAddition = getShorthandIndentAddition();
    advanceLexer();
    eolSeen = false;

    parseBlockNode(indent + indentAddition, true);
    rollBackToEol();
    return YAMLElementTypes.SEQUENCE_ITEM;
  }

  @NotNull
  private IElementType parseHash() {
    assert getTokenType() == LBRACE;
    advanceLexer();
    myStopTokensStack.add(HASH_STOP_TOKENS);

    while (!eof()) {
      if (getTokenType() == RBRACE) {
        advanceLexer();
        break;
      }
      parseSingleStatement(0, 0);
    }

    myStopTokensStack.pop();
    dropEolMarker();
    return YAMLElementTypes.HASH;
  }

  @NotNull
  private IElementType parseArray() {
    assert getTokenType() == LBRACKET;
    advanceLexer();
    myStopTokensStack.add(ARRAY_STOP_TOKENS);

    while (!eof()) {
      if (getTokenType() == RBRACKET) {
        advanceLexer();
        break;
      }
      if (isJunk()) {
        advanceLexer();
        continue;
      }

      final PsiBuilder.Marker marker = mark();
      final IElementType parsedElement = parseSingleStatement(0, 0);
      if (parsedElement != null) {
        marker.done(YAMLElementTypes.SEQUENCE_ITEM);
      }
      else {
        marker.error(YAMLBundle.message("parsing.error.sequence.item.expected"));
      }

      if (getTokenType() == YAMLTokenTypes.COMMA) {
        advanceLexer();
      }
    }

    myStopTokensStack.pop();
    dropEolMarker();
    return YAMLElementTypes.ARRAY;
  }

  private boolean eof() {
    return myBuilder.eof() || myBuilder.getTokenType() == DOCUMENT_MARKER;
  }

  @Nullable
  private IElementType getTokenType() {
    return eof() ? null : myBuilder.getTokenType();
  }

  private void dropEolMarker() {
    if (myAfterLastEolMarker != null) {
      myAfterLastEolMarker.drop();
      myAfterLastEolMarker = null;
    }
  }

  private void rollBackToEol() {
    if (eolSeen && myAfterLastEolMarker != null) {
      eolSeen = false;
      myAfterLastEolMarker.rollbackTo();
      myAfterLastEolMarker = null;
    }
  }

  private PsiBuilder.Marker mark() {
    dropEolMarker();
    return myBuilder.mark();
  }

  private void advanceLexer() {
    if (myBuilder.eof()) {
      return;
    }
    final IElementType type = myBuilder.getTokenType();
    boolean eolElement = YAMLElementTypes.EOL_ELEMENTS.contains(type);
    eolSeen = eolSeen || eolElement;
    if (eolElement) {
      // Drop and create new eolMarker
      myAfterLastEolMarker = mark();
      myIndent = 0;
    }
    else if (type == INDENT) {
      myIndent = getCurrentTokenLength();
    }
    else {
      // Drop Eol Marker if other token seen
      dropEolMarker();
    }
    myBuilder.advanceLexer();
  }

  private int getCurrentTokenLength() {
    return myBuilder.rawTokenTypeStart(1) - myBuilder.getCurrentOffset();
  }

  private void passJunk() {
    while (!eof() && isJunk()) {
      advanceLexer();
    }
  }

  private boolean isJunk() {
    final IElementType type = getTokenType();
    return type == INDENT || type == EOL;
  }

  private static int findLeftRange(@NotNull List<? extends IElementType> tokens) {
    int i = tokens.indexOf(COMMENT);
    return i != -1 ? i : tokens.size();
  }
}