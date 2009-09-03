package org.jetbrains.yaml.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;

/**
 * @author oleg
 */
public class YAMLParser implements PsiParser, YAMLTokenTypes {
  private PsiBuilder myBuilder;
  private boolean eolSeen = false;
  private int myIndent;

  @NotNull
  public ASTNode parse(final IElementType root, final PsiBuilder builder) {
    myBuilder = builder;
    final PsiBuilder.Marker fileMarker = myBuilder.mark();
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
  }

  private void parseDocument() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (myBuilder.getTokenType() == DOCUMENT_MARKER){
      advanceLexer();
    }
    parseStatements(0);
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
    if (SCALAR_VALUES.contains(getTokenType())){
      parseScalarValue();
    } else {
      advanceLexer();
    }
  }

  private void parseScalarValue() {
    final IElementType tokenType = getTokenType();
    assert SCALAR_VALUES.contains(tokenType) : "Scalar value expected!";
    if (tokenType == SCALAR_LIST || tokenType == SCALAR_TEXT){
      parseMultiLineScalar(tokenType);
    } else {
      advanceLexer();
    }
  }

  private void parseMultiLineScalar(final IElementType tokenType) {
    final PsiBuilder.Marker marker = myBuilder.mark();
    PsiBuilder.Marker rollBackMarker = null;
    IElementType type = getTokenType();
    while (type == tokenType || type == INDENT || type == EOL){
      advanceLexer();
      if (type == tokenType) {
        if (rollBackMarker != null){
          rollBackMarker.drop();
        }
        rollBackMarker = myBuilder.mark();
      }
      type = getTokenType();
    }
    rollBackMarker.rollbackTo();
    marker.done(tokenType == SCALAR_LIST ? YAMLElementTypes.SCALAR_LIST_VALUE : YAMLElementTypes.SCALAR_TEXT_VALUE);
  }

  private void parseScalarKeyValue(int indent) {
    final PsiBuilder.Marker marker = myBuilder.mark();
    assert getTokenType() == SCALAR_KEY : "Expected scalar key";
    eolSeen = false;
    advanceLexer();
    passJunk();
    if (SCALAR_VALUES.contains(getTokenType())){
      parseScalarValue();
    } else {
      final PsiBuilder.Marker valueMarker = myBuilder.mark();
      if (!eolSeen) {
        parseSingleStatement(myIndent);
      } else {
        while (!eof() && (isJunk() || (myIndent > indent))) {
          parseStatements(myIndent);
        }
      }
      valueMarker.done(YAMLElementTypes.COMPOUND_VALUE);
    }
    marker.done(YAMLElementTypes.KEY_VALUE_PAIR);
  }

  private void parseSequence() {
    final int indent = myIndent + 2;
    final PsiBuilder.Marker sequenceMarker = myBuilder.mark();
    advanceLexer();
    eolSeen = false;
    passJunk();
    parseStatements(indent);
    sequenceMarker.done(YAMLElementTypes.SEQUENCE);
  }

  private void parseHash() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    advanceLexer();
    while (!eof()){
      if (getTokenType() == RBRACE){
        break;
      }
      parseSingleStatement(0);
    }
    marker.done(YAMLElementTypes.HASH);
  }

  private void parseArray() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    advanceLexer();
    while (!eof()){
      if (getTokenType() == RBRACKET){
        break;
      }
      parseSingleStatement(0);
    }
    marker.done(YAMLElementTypes.ARRAY);
  }

  private boolean eof() {
    return myBuilder.eof() || myBuilder.getTokenType() == DOCUMENT_MARKER;
  }

  private IElementType getTokenType() {
    return eof() ? null : myBuilder.getTokenType();
  }

  private void advanceLexer() {
    if (myBuilder.eof()){
      return;
    }
    final IElementType type = myBuilder.getTokenType();
    eolSeen = eolSeen || type == EOL;
    if (type == EOL){
      myIndent = 0;
    }
    if (type == INDENT){
      myIndent = myBuilder.getTokenText().length();
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