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

import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import static org.intellij.plugins.relaxNG.compact.RncTokenTypes.*;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;

import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 09.08.2007
*/
public class PatternParsing extends DeclarationParsing {

  @SuppressWarnings({ "unchecked" })
  protected static final Map<IElementType, IElementType> TOKEN_MAP =
          new THashMap<IElementType, IElementType>(TObjectHashingStrategy.IDENTITY);

  static {
    TOKEN_MAP.put(COMMA, RncElementTypes.SEQUENCE);
    TOKEN_MAP.put(PIPE, RncElementTypes.CHOICE);
    TOKEN_MAP.put(AND, RncElementTypes.INTERLEAVE);
    TOKEN_MAP.put(STAR, RncElementTypes.ZERO_OR_MORE);
    TOKEN_MAP.put(PLUS, RncElementTypes.ONE_OR_MORE);
    TOKEN_MAP.put(QUEST, RncElementTypes.OPTIONAL);
  }

  private final NameClassParsing myNameClassParsing;

  public PatternParsing(PsiBuilder builder) {
    super(builder);
    myNameClassParsing = new NameClassParsing(builder);
  }

  public void parse() {
    parseTopLevel();
  }

  protected boolean parsePattern() {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseQuantifiedPattern()) {
      marker.drop();
      return false;
    }

    while (BINARY_OPS.contains(currentToken())) {
      IElementType t;
      if (BINARY_OPS.contains(t = currentToken())) {
        do {
          advance();
          if (!parseQuantifiedPattern()) {
            error("Pattern expected");
          }
        } while (currentToken() == t);
        marker.done(TOKEN_MAP.get(t));
        marker = marker.precede();
      }
    }
    marker.drop();
    return true;
  }

  private boolean parseQuantifiedPattern() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseSinglePattern()) {
      marker.drop();
      return false;
    }

    final IElementType t = currentToken();
    if (matches(QUANTIFIER_OPS)) {
      marker.done(TOKEN_MAP.get(t));
    } else {
      marker.drop();
    }
    return true;
  }

  private boolean parseSinglePattern() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (matches(ATTR_OR_ELEMENT)) {
      if (!myNameClassParsing.parseNameClass()) {
        error("Name class expected");
        marker.drop();
        return false;
      }
      parseBracedPattern();
      marker.done(RncElementTypes.PATTERN);
    } else if (matches(KEYWORD_LIST)) {
      parseBracedPattern();
      marker.done(RncElementTypes.LIST_PATTERN);
    } else if (matches(KEYWORD_MIXED)) {
      parseBracedPattern();
      marker.done(RncElementTypes.MIXED_PATTERN);
    } else if (matches(KEYWORD_EXTERNAL)) {
      parseAnyUriLiteral();
      parseInherit();
      marker.done(RncElementTypes.EXTERNAL_REF);
    } else if (matches(KEYWORD_NOT_ALLOWED)) {
      marker.done(RncElementTypes.NOT_ALLOWED_PATTERN);
    } else if (matches(KEYWORD_TEXT)) {
      marker.done(RncElementTypes.TEXT_PATTERN);
    } else if (matches(KEYWORD_EMPTY)) {
      marker.done(RncElementTypes.EMPTY_PATTERN);
    } else if (matches(KEYWORD_PARENT)) {
      match(IDENTIFIERS, "Identifier expected");
      marker.done(RncElementTypes.PARENT_REF);
    } else if (matches(KEYWORD_GRAMMAR)) {
      parseBracedGrammarContents();
      marker.done(RncElementTypes.GRAMMAR_PATTERN);
    } else if (matches(LPAREN)) {
      if (!parsePattern()) {
        error("Pattern expected");
      }
      match(RPAREN, "')' expected");
      marker.done(RncElementTypes.GROUP_PATTERN);
    } else if (matches(IDENTIFIERS)) {
      marker.done(RncElementTypes.REF_PATTERN);
    } else if (matches(LA_DATATYPE)) {
      parseDatatype();
      marker.done(RncElementTypes.DATATYPE_PATTERN);
    } else if (currentToken() == PREFIXED_NAME) {
      makeName();
      parseDatatype();
      marker.done(RncElementTypes.DATATYPE_PATTERN);
    } else if (matches(LITERAL)) {
      marker.done(RncElementTypes.DATATYPE_PATTERN);
    } else {
      marker.drop();
      return false;
    }
    return true;
  }

  private void parseDatatype() {
    if (currentToken() == LITERAL) {
      advance();
    } else if (matches(LBRACE)) {
      parseParams();
      if (matches(MINUS)) {
        if (!parsePattern()) {
          error("Pattern expected");
        }
      }
      match(RBRACE, "'}' expected");
    }
  }

  private void parseBracedPattern() {
    match(LBRACE, "'{' expected");
    if (!parsePattern()) {
      error("Pattern expected");
    }
    match(RBRACE, "'}' expected");
  }

  private void parseParams() {
    final IElementType t = currentToken();
    if (t != RBRACE) {
      do {
        final PsiBuilder.Marker marker = myBuilder.mark();
        match(IDENTIFIER_OR_KEYWORD, "Identifier expected");
        match(EQ, "'=' expected");
        match(LITERAL, "Literal expected");
        marker.done(RncElementTypes.PARAM);
      } while (IDENTIFIER_OR_KEYWORD.contains(currentToken()));
    }
  }
}
