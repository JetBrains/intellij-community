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

public class NameClassParsing extends AbstractParsing {

  public NameClassParsing(PsiBuilder builder) {
    super(builder);
  }

  public boolean parseNameClass() {
    PsiBuilder.Marker marker = myBuilder.mark();

    if (!parseNameClassPart()) {
      marker.drop();
      return false;
    }

    if (currentToken() == PIPE) {
      do {
        advance();
        if (!parseNameClassPart()) {
          error("NameClass expected");
        }
      } while (currentToken() == PIPE);
      marker.done(RncElementTypes.NAME_CLASS_CHOICE);
    } else {
      marker.drop();
    }
    return true;
  }

  private boolean parseNameClassPart() {
    final IElementType t = currentToken();
    PsiBuilder.Marker marker = myBuilder.mark();
    if (IDENTIFIER_OR_KEYWORD.contains(t)) {
      advance();
    } else if (PREFIXED_NAME == t) { // CName
      makeName();
    } else if (STAR == t) { // anyName
      advance();
      parseExceptNameClass();
    } else if (PREFIXED_STAR == t) { // nsName
      makeName();
      parseExceptNameClass();
    } else if (LPAREN == t) {
      advance();
      if (parseNameClass()) {
        match(RPAREN, "')' expected");
      }
    } else {
      marker.drop();
      return false;
    }
    marker.done(RncElementTypes.NAME_CLASS);
    return true;
  }

  private void parseExceptNameClass() {
    if (MINUS == currentToken()) {
      final PsiBuilder.Marker marker = begin();
      if (!parseNameClass()) {
        error("NameClass expected");
      }
      marker.done(RncElementTypes.EXCEPT_NAME_CLASS);
    }
  }
}
