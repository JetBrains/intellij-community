/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.lexer.LookAheadLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import java.io.IOException;

public final class XPathLexer extends LookAheadLexer {
  private static final TokenSet WS_OR_COMMENT = TokenSet.create(XPathTokenTypes.WHITESPACE, XPath2TokenTypes.COMMENT);

  private final boolean myXPath2Syntax;

  private XPathLexer(boolean xpath2Syntax) {
    super(new FlexAdapter(new _XPathLexer(xpath2Syntax) {
      @Override
      protected void readComment() throws IOException {
        final int state = yystate();
        final int start = getTokenStart();
        while (true) {
          final IElementType type = advance();
          if (type == null || type == XPath2TokenTypes.END_COMMENT) {
            break;
          }
        }
        setStart(start);
        yybegin(state);
      }
    }));

    myXPath2Syntax = xpath2Syntax;
  }

  @Override
  protected void lookAhead(Lexer baseLexer) {
    final IElementType type = baseLexer.getTokenType();

    if (type == XPath2TokenTypes.IF) { // "if" / "(" EXPRESSION
      if (replaceTokenNotFollowedBy(baseLexer, XPathTokenTypes.NCNAME, XPathTokenTypes.LPAREN)) {
        return;
      }
    } else if (type == XPathTokenTypes.NCNAME) {
      if (handleNCName(baseLexer, type)) return;
    } else if (type == XPathTokenTypes.RPAREN) {
      if (getState() == _XPathLexer.TYPE) {
        if (!lookingAt(baseLexer, XPath2TokenTypes.OCCURRENCE_OPS)) {
          setState(baseLexer, _XPathLexer.YYINITIAL);
        }
      }
    } else if (type == XPathTokenTypes.AXIS_NAME) {
      if (replaceTokenNotFollowedBy(baseLexer, XPathTokenTypes.NCNAME, XPathTokenTypes.COLCOL)) { // AXIS_NAME / "::"
        setState(baseLexer, _XPathLexer.YYINITIAL);
        return;
      }
    } else if (type == XPathTokenTypes.NODE_TYPE) {
      final String text = baseLexer.getTokenText();
      // NODE_TYPE2
      if ("attribute".equals(text) || "element".equals(text) || "schema-element".equals(text) || "schema-attribute".equals(text) || "document-node".equals(text)) {
        // "attribute::" vs. "attribute("
        if ("attribute".equals(text)) {
          if (replaceTokenFollowedBy(baseLexer, XPathTokenTypes.AXIS_NAME, XPathTokenTypes.COLCOL)) {
            setState(baseLexer, _XPathLexer.S1);
            return;
          }
        }

        if (myXPath2Syntax) {
          if (!replaceTokenNotFollowedBy(baseLexer, XPathTokenTypes.NCNAME, XPathTokenTypes.LPAREN)) {
            advanceAs(baseLexer, type);
          }
        } else {
          handleNCName(baseLexer, XPathTokenTypes.NCNAME);
        }
        return;
      } else if (replaceTokenNotFollowedBy(baseLexer, XPathTokenTypes.NCNAME, XPathTokenTypes.LPAREN)) {
        // other NODE_TYPEs
        setState(baseLexer, _XPathLexer.YYINITIAL);
        return;
      }
    } else if (XPath2TokenTypes.QUANTIFIERS.contains(type) || type == XPath2TokenTypes.FOR) { // ("some" | "every" | "for") / "$" VARNAME
      if (replaceTokenNotFollowedBy(baseLexer, XPathTokenTypes.NCNAME, XPathTokenTypes.DOLLAR)) {
        return;
      }
    }

    advanceAs(baseLexer, type);
  }

  private boolean handleNCName(Lexer baseLexer, IElementType type) {
    if (baseLexer.getState() == _XPathLexer.TYPE) {
      if (!lookingAt(baseLexer, XPathTokenTypes.COL, XPathTokenTypes.NCNAME) && !lookingAt(baseLexer, XPath2TokenTypes.OCCURRENCE_OPS)) {
        setState(baseLexer, _XPathLexer.S1);
      }
    } else {
      if (!replaceTokenFollowedBy(baseLexer, XPathTokenTypes.FUNCTION_NAME, XPathTokenTypes.LPAREN)) { // NCNAME / "(" -> FUNCTION_NAME
        if (lookingAt(baseLexer, XPathTokenTypes.COL, XPathTokenTypes.NCNAME, XPathTokenTypes.LPAREN)) { // NCNAME / ":" NCNAME "(" -> EXT_PREFIX
          advanceAs(baseLexer, XPathTokenTypes.EXT_PREFIX);
        } else {
          if (!replaceTokenFollowedBy(baseLexer, XPathTokenTypes.BAD_AXIS_NAME, XPathTokenTypes.COLCOL)) { // NCNAME / "::" -> BAD_AXIS_NAME
            advanceAs(baseLexer, type);
          }
        }
      }
      return true;
    }
    return false;
  }

  private static boolean lookingAt(Lexer lexer, TokenSet tokenSet) {
    final LexerPosition position = lexer.getCurrentPosition();
    try {
      lexer.advance();
      skipWhitespaceAnComments(lexer);
      return tokenSet.contains(lexer.getTokenType());
    } finally {
      lexer.restore(position);
    }
  }

  private static void setState(Lexer baseLexer, int state) {
    ((FlexAdapter)baseLexer).getFlex().yybegin(state);
  }

  private boolean replaceTokenNotFollowedBy(Lexer baseLexer, final IElementType replacement, IElementType nextToken) {
    if (!lookingAt(baseLexer, nextToken)) {
      treatAs(baseLexer, replacement);
      return true;
    }
    return false;
  }

  private boolean replaceTokenFollowedBy(Lexer baseLexer, final IElementType replacement, IElementType nextToken) {
    if (lookingAt(baseLexer, nextToken)) {
      treatAs(baseLexer, replacement);
      return true;
    }
    return false;
  }

  private void treatAs(Lexer lexer, IElementType type) {
    if (type == XPathTokenTypes.NCNAME) {
      if (!handleNCName(lexer, type)) {
        advanceAs(lexer, type);
      }
    } else {
      advanceAs(lexer, type);
    }
  }

  private static boolean lookingAt(Lexer baseLexer, IElementType... tokens) {
    final LexerPosition position = baseLexer.getCurrentPosition();
    try {
      for (IElementType token : tokens) {
        baseLexer.advance();
        skipWhitespaceAnComments(baseLexer);
        if (baseLexer.getTokenType() != token) {
          return false;
        }
      }
      return true;
    } finally {
      baseLexer.restore(position);
    }
  }

  private static void skipWhitespaceAnComments(Lexer baseLexer) {
    while (WS_OR_COMMENT.contains(baseLexer.getTokenType())) {
      baseLexer.advance();
    }
  }

  public static Lexer create(boolean xpath2Syntax) {
    return new XPathLexer(xpath2Syntax);
  }
}
