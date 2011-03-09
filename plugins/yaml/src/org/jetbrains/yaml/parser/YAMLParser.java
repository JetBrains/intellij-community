package org.jetbrains.yaml.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  @NotNull
  public ASTNode parse(final IElementType root, final PsiBuilder builder) {
    myBuilder = builder;
    builder.setDebugMode(true);
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
    if (myBuilder.getTokenType() == DOCUMENT_MARKER){
      advanceLexer();
    }
    parseStatements(0);
    dropEolMarker();
    marker.done(YAMLElementTypes.DOCUMENT);
  }

  private void parseStatements(int indent) {
    while (!eof() && (isJunk() || !eolSeen || myIndent >= indent)) {
      parseSingleStatement(indent);
    }
  }

  private void parseSingleStatement(int indent) {
    if (eof()){
      return;
    }
    final IElementType tokenType = getTokenType();
    if (tokenType == LBRACE){
      parseHash();
    } else
    if (tokenType == LBRACKET){
      parseArray();
    } else
    if (tokenType == SEQUENCE_MARKER){
      parseSequence();
    } else
    if (tokenType == SCALAR_KEY) {
      parseScalarKeyValue(indent);
    } else
    if (YAMLElementTypes.SCALAR_VALUES.contains(getTokenType())){
      parseScalarValue();
    } else {
      advanceLexer();
    }
  }

  private void parseScalarValue() {
    final IElementType tokenType = getTokenType();
    assert YAMLElementTypes.SCALAR_VALUES.contains(tokenType) : "Scalar value expected!";
    if (tokenType == SCALAR_LIST || tokenType == SCALAR_TEXT){
      parseMultiLineScalar(tokenType);
    } else {
      advanceLexer();
    }
  }

  private void parseMultiLineScalar(final IElementType tokenType) {
    final PsiBuilder.Marker marker = mark();
    IElementType type = getTokenType();
    while (type == tokenType || type == INDENT || type == EOL){
      advanceLexer();
      type = getTokenType();
    }
    rollBackToEol();
    marker.done(tokenType == SCALAR_LIST ? YAMLElementTypes.SCALAR_LIST_VALUE : YAMLElementTypes.SCALAR_TEXT_VALUE);
  }

  private void parseScalarKeyValue(int indent) {
    final PsiBuilder.Marker marker = mark();
    assert getTokenType() == SCALAR_KEY : "Expected scalar key";
    eolSeen = false;
    advanceLexer();
    passJunk();
    if (YAMLElementTypes.SCALAR_VALUES.contains(getTokenType())){
      parseScalarValue();
    } else {
      final PsiBuilder.Marker valueMarker = mark();
      if (!eolSeen) {
        parseSingleStatement(myIndent);
      } else {
        while (!eof() && (isJunk() || (myIndent > indent))) {
          parseStatements(myIndent);
        }
      }
      rollBackToEol();
      valueMarker.done(YAMLElementTypes.COMPOUND_VALUE);
    }
    marker.done(YAMLElementTypes.KEY_VALUE_PAIR);
  }

  private void parseSequence() {
    final int indent = myIndent + 2;
    final PsiBuilder.Marker sequenceMarker = mark();
    advanceLexer();
    eolSeen = false;
    passJunk();
    parseStatements(indent);
    rollBackToEol();
    sequenceMarker.done(YAMLElementTypes.SEQUENCE);
  }

  private void parseHash() {
    final PsiBuilder.Marker marker = mark();
    advanceLexer();
    while (!eof()){
      if (getTokenType() == RBRACE){
        break;
      }
      parseSingleStatement(0);
    }
    dropEolMarker();
    marker.done(YAMLElementTypes.HASH);
  }

  private void parseArray() {
    final PsiBuilder.Marker marker = mark();
    advanceLexer();
    while (!eof()){
      if (getTokenType() == RBRACKET){
        break;
      }
      parseSingleStatement(0);
    }
    dropEolMarker();
    marker.done(YAMLElementTypes.ARRAY);
  }

  private boolean eof() {
    return myBuilder.eof() || myBuilder.getTokenType() == DOCUMENT_MARKER;
  }

  @Nullable
  private IElementType getTokenType() {
    return eof() ? null : myBuilder.getTokenType();
  }

  private void dropEolMarker(){
    if (myAfterLastEolMarker != null){
      myAfterLastEolMarker.drop();
      myAfterLastEolMarker = null;
    }
  }

  private void rollBackToEol() {
    if (eolSeen && myAfterLastEolMarker != null){
      eolSeen = false;
      myAfterLastEolMarker.rollbackTo();
      myAfterLastEolMarker = null;
    }
  }

  private PsiBuilder.Marker mark(){
    dropEolMarker();
    return myBuilder.mark();
  }

  private void advanceLexer() {
    if (myBuilder.eof()){
      return;
    }
    final IElementType type = myBuilder.getTokenType();
    eolSeen = eolSeen || type == EOL;
    if (type == EOL){
      // Drop and create new eolMarker
      myAfterLastEolMarker = mark();
      myIndent = 0;
    } else
    if (type == INDENT){
      myIndent = myBuilder.getTokenText().length();
    } else {
      // Drop Eol Marker if other token seen
      dropEolMarker();
    }
    myBuilder.advanceLexer();
  }

  private void passJunk() {
    while (!eof() && isJunk()){
      advanceLexer();
    }
  }

  private boolean isJunk(){
    final IElementType type = getTokenType();
    return type == INDENT || type == EOL;
  }
}