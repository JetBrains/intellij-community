/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;

import static org.intellij.plugins.relaxNG.compact.RncTokenTypes.*;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 09.08.2007
 */
public abstract class AbstractParsing {
  protected static final TokenSet LA_INCLUDE_CONTENT = TokenSet.orSet(TokenSet.create(
          KEYWORD_DIV, KEYWORD_START), IDENTIFIERS);

  protected static final TokenSet LA_GRAMMAR_CONTENT = TokenSet.orSet(TokenSet.create(
          KEYWORD_INCLUDE), LA_INCLUDE_CONTENT);

  protected static final TokenSet ATTR_OR_ELEMENT = TokenSet.create(
          KEYWORD_ATTRIBUTE, KEYWORD_ElEMENT);

  protected static final TokenSet CONTENT = TokenSet.create(
          KEYWORD_LIST, KEYWORD_MIXED);

  protected static final TokenSet LA_DATATYPE = TokenSet.create(
          KEYWORD_STRING, KEYWORD_TOKEN);

  protected static final TokenSet LA_DECL = TokenSet.create(
          KEYWORD_DATATYPES, KEYWORD_NAMESPACE, KEYWORD_DEFAULT);

  protected static final TokenSet NS_URI_LITERAL = TokenSet.create(KEYWORD_INHERIT, LITERAL);
  protected static final TokenSet ASSIGN_METHOD = TokenSet.create(EQ, CHOICE_EQ, INTERLEAVE_EQ);
  protected final PsiBuilder myBuilder;

  public AbstractParsing(PsiBuilder builder) {
    myBuilder = builder;
  }

  protected final PsiBuilder.Marker begin() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    advance();
    return marker;
  }

  protected final boolean matches(TokenSet set) {
    if (set.contains(currentToken())) {
      advance();
      return true;
    }
    return false;
  }

  protected final boolean matches(IElementType t) {
    if (t == currentToken()) {
      advance();
      return true;
    }
    return false;
  }

  protected final void match(IElementType token, String msg) {
    if (!matches(token)) {
      error(msg);
      advance();
    }
  }

  protected final void match(TokenSet set, String msg) {
    if (!matches(set)) {
      error(msg);
      advance();
    }
  }

  protected final void error(String s) {
    myBuilder.error(s);
  }

  protected final void advance() {
    myBuilder.advanceLexer();
  }

  protected final IElementType currentToken() {
    final IElementType token = myBuilder.getTokenType();
    if (isName(token)) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      makeName();
      if (myBuilder.getTokenType() == LBRACKET) {
        skipAnnotation(marker, RncElementTypes.ANNOTATION_ELEMENT);
        return currentToken();
      } else {
        marker.rollbackTo();
      }
    } else if (token == LBRACKET) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      skipAnnotation(marker, RncElementTypes.ANNOTATION);
      return currentToken();
    } else if (token == GTGT) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      advance();
      if (isName(myBuilder.getTokenType())) {
        makeName();
        if (myBuilder.getTokenType() == LBRACKET) {
          skipAnnotation(marker, RncElementTypes.FORWARD_ANNOTATION);
          return currentToken();
        }
      }
      marker.done(RncElementTypes.FORWARD_ANNOTATION);
      return currentToken();
    }
    return token;
  }

  protected static boolean isName(IElementType token) {
    return IDENTIFIER_OR_KEYWORD.contains(token) || token == PREFIXED_NAME;
  }

  private void skipAnnotation(PsiBuilder.Marker marker, IElementType annotationType) {
    final boolean b = myBuilder.getTokenType() == LBRACKET;
    advance();
    assert b;

    while (!myBuilder.eof() && currentToken() != RBRACKET) {
      advance();
    }
    if (myBuilder.getTokenType() == RBRACKET) {
      advance();
    }
    marker.done(annotationType);
  }

  protected final void makeName() {
    final PsiBuilder.Marker name = myBuilder.mark();
    advance();
    name.done(RncElementTypes.NAME);
  }
}
