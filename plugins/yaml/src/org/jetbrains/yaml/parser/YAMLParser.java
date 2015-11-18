package org.jetbrains.yaml.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementType;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;

/**
 * @author oleg
 */
public class YAMLParser implements PsiParser, YAMLTokenTypes {
  private PsiBuilder myBuilder;
  private boolean eolSeen = false;
  private int myIndent;
  private PsiBuilder.Marker myAfterLastEolMarker;

  private final Stack<TokenSet> myStopTokensStack = new Stack<TokenSet>();

  @NotNull
  public ASTNode parse(@NotNull final IElementType root, @NotNull final PsiBuilder builder) {
    myBuilder = builder;
    myStopTokensStack.clear();
    final PsiBuilder.Marker fileMarker = mark();
    parseFile();
    assert myBuilder.eof() : "Not all tokens were passed.";
    fileMarker.done(root);
    return builder.getTreeBuilt();
  }

  private void parseFile() {
    passJunk();
    parseDocument();
    passJunk();
    while (!myBuilder.eof()) {
      parseDocument();
      passJunk();
    }
    dropEolMarker();
  }

  private void parseDocument() {
    final PsiBuilder.Marker marker = mark();
    if (myBuilder.getTokenType() == DOCUMENT_MARKER) {
      advanceLexer();
    }
    parseStatements(0, false);
    dropEolMarker();
    marker.done(YAMLElementTypes.DOCUMENT);
  }

  private void parseStatements(int indent, boolean insideSequence) {
    passJunk();

    final PsiBuilder.Marker marker = mark();
    IElementType nodeType = null;

    if (getTokenType() == YAMLTokenTypes.TAG) {
      advanceLexer();
    }

    while (!eof() && (isJunk() || !eolSeen || myIndent + getIndentBonus(insideSequence) >= indent)) {
      if (isJunk()) {
        advanceLexer();
        continue;
      }

      if (!myStopTokensStack.isEmpty() && myStopTokensStack.peek().contains(getTokenType())) {
        rollBackToEol();
        break;
      }

      final IElementType parsedTokenType = parseSingleStatement(eolSeen ? myIndent : indent);
      if (nodeType == null) {
        if (parsedTokenType == YAMLElementTypes.SEQUENCE_ITEM) {
          nodeType = YAMLElementTypes.SEQUENCE;
        }
        else if (parsedTokenType == YAMLElementTypes.KEY_VALUE_PAIR) {
          nodeType = YAMLElementTypes.MAPPING;
        }
        else {
          nodeType = YAMLElementTypes.COMPOUND_VALUE;
        }
      }
    }

    rollBackToEol();
    if (nodeType == null) {
      nodeType = YAMLElementTypes.COMPOUND_VALUE;
    }
    marker.done(nodeType);
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
  private IElementType parseSingleStatement(int indent) {
    if (eof()) {
      return null;
    }
    final IElementType tokenType = getTokenType();
    if (tokenType == LBRACE) {
      return parseHash();
    }
    else if (tokenType == LBRACKET) {
      return parseArray();
    }
    else if (tokenType == SEQUENCE_MARKER) {
      return parseSequenceItem(indent);
    }
    else if (tokenType == SCALAR_KEY) {
      return parseScalarKeyValue(indent);
    }
    else if (YAMLElementTypes.SCALAR_VALUES.contains(getTokenType())) {
      return parseScalarValue(indent);
    }
    else {
      advanceLexer();
      return null;
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
    else {
      advanceLexer();
      return null;
    }
  }

  @NotNull
  private IElementType parseMultiLineScalar(final IElementType tokenType) {
    final PsiBuilder.Marker marker = mark();
    IElementType type = getTokenType();
    while (type == tokenType || type == INDENT || type == EOL) {
      advanceLexer();
      type = getTokenType();
    }
    rollBackToEol();
    final YAMLElementType resultType = tokenType == SCALAR_LIST ? YAMLElementTypes.SCALAR_LIST_VALUE : YAMLElementTypes.SCALAR_TEXT_VALUE;
    marker.done(resultType);
    return resultType;
  }

  @NotNull
  private IElementType parseMultiLinePlainScalar(final int indent) {
    final PsiBuilder.Marker marker = mark();
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
    marker.done(YAMLElementTypes.SCALAR_PLAIN_VALUE);
    return YAMLElementTypes.SCALAR_PLAIN_VALUE;
  }

  @NotNull
  private IElementType parseScalarKeyValue(int indent) {
    final PsiBuilder.Marker marker = mark();
    assert getTokenType() == SCALAR_KEY : "Expected scalar key";
    eolSeen = false;

    int indentAddition = getShorthandIndentAddition();
    advanceLexer();
    passJunk();

    parseStatements(indent + indentAddition, false);

    marker.done(YAMLElementTypes.KEY_VALUE_PAIR);
    return YAMLElementTypes.KEY_VALUE_PAIR;
  }

  @NotNull
  private IElementType parseSequenceItem(int indent) {
    final PsiBuilder.Marker sequenceMarker = mark();
    assert getTokenType() == SEQUENCE_MARKER;

    int indentAddition = getShorthandIndentAddition();
    advanceLexer();
    eolSeen = false;
    passJunk();

    parseStatements(indent + indentAddition, true);
    rollBackToEol();
    sequenceMarker.done(YAMLElementTypes.SEQUENCE_ITEM);
    return YAMLElementTypes.SEQUENCE_ITEM;
  }

  @NotNull
  private IElementType parseHash() {
    final PsiBuilder.Marker marker = mark();
    assert getTokenType() == LBRACE;
    advanceLexer();
    myStopTokensStack.add(TokenSet.create(RBRACE, COMMA));

    while (!eof()) {
      if (getTokenType() == RBRACE) {
        advanceLexer();
        break;
      }
      parseSingleStatement(0);
    }

    myStopTokensStack.pop();
    dropEolMarker();
    marker.done(YAMLElementTypes.HASH);
    return YAMLElementTypes.HASH;
  }

  @NotNull
  private IElementType parseArray() {
    final PsiBuilder.Marker marker = mark();
    assert getTokenType() == LBRACKET;
    advanceLexer();
    myStopTokensStack.add(TokenSet.create(RBRACKET, COMMA));

    while (!eof()) {
      if (getTokenType() == RBRACKET) {
        advanceLexer();
        break;
      }
      parseSingleStatement(0);
    }

    myStopTokensStack.pop();
    dropEolMarker();
    marker.done(YAMLElementTypes.ARRAY);
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
    eolSeen = eolSeen || type == EOL;
    if (type == EOL) {
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
}