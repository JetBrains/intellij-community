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

package org.intellij.plugins.relaxNG.compact;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.rngom.parse.compact.CompactSyntaxConstants;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 04.08.2007
 */
public class RncTokenTypes {
  private static final TIntObjectHashMap<IElementType> ourTokenTypes = new TIntObjectHashMap<>();

  static {
    assert RngCompactLanguage.INSTANCE != null;
    try {
      final Field[] fields = CompactSyntaxConstants.class.getFields();
      for (Field field : fields) {
        final String name = field.getName();
        if (name.equals("DEFAULT")) break;

        if (int.class.isAssignableFrom(field.getType())) {
          Integer i = (Integer)field.get(null);
          ourTokenTypes.put(i, new RncElementType(name));
        }
      }

      final String[] tokens = CompactSyntaxConstants.tokenImage;
      for (int i = 0; i < tokens.length; i++) {
        String token = tokens[i];
        if (token.matches("\"\\w*\"")) {
          token = "KEYWORD_" + token.substring(1, token.length() - 1).toUpperCase(Locale.US);
        } else if (token.matches("\".*\"")) {
          token = token.substring(1, token.length() - 1);
        }
        if (ourTokenTypes.get(i) == null) {
          ourTokenTypes.put(i, new RncElementType(token));
        }
      }
    } catch (IllegalAccessException e) {
      throw new Error(e);
    }
  }

  @NotNull
  public static IElementType get(int i) {
    assert !ourTokenTypes.isEmpty();
    final IElementType type = ourTokenTypes.get(i);
    assert type != null : "Unknown token kind: " + i;
    return type;
  }

  @NotNull
  private static IElementType get(final String name) {
    assert !ourTokenTypes.isEmpty();
    final Ref<IElementType> ref = new Ref<>();
    ourTokenTypes.forEachValue(new TObjectProcedure<IElementType>() {
      @Override
      public boolean execute(IElementType iElementType) {
        if (iElementType.toString().equals(name)) {
          ref.set(iElementType);
          return false;
        }
        return true;
      }
    });
    return ref.get();
  }

  private static final IElementType WS = get(CompactSyntaxConstants.WS);
  public static final TokenSet WHITESPACE = TokenSet.create(WS);

  public static final IElementType ILLEGAL_CHAR = get(CompactSyntaxConstants.ILLEGAL_CHAR);
  
  public static final IElementType LBRACE = get("{");
  public static final IElementType RBRACE = get("}");
  public static final IElementType LBRACKET = get("[");
  public static final IElementType RBRACKET = get("]");
  public static final IElementType LPAREN = get("(");
  public static final IElementType RPAREN = get(")");

  public static final IElementType GTGT = get(CompactSyntaxConstants.FANNOTATE);

  public static final TokenSet BRACES = TokenSet.create(LBRACE, RBRACE);

  public static final IElementType EQ = get("=");
  public static final IElementType PLUS = get("+");
  public static final IElementType MINUS = get("-");
  public static final IElementType STAR = get("*");
  public static final IElementType AND = get("&");
  public static final IElementType PIPE = get("|");
  public static final IElementType QUEST = get("?");

  public static final IElementType PREFIXED_NAME = get(CompactSyntaxConstants.PREFIXED_NAME);
  public static final IElementType PREFIXED_STAR = get(CompactSyntaxConstants.PREFIX_STAR);

  public static final IElementType CHOICE_EQ = get("|=");
  public static final IElementType INTERLEAVE_EQ = get("&=");

  public static final IElementType COMMA = get(",");

  public static final TokenSet BINARY_OPS = TokenSet.create(
          COMMA, PIPE, AND
  );

  public static final TokenSet QUANTIFIER_OPS = TokenSet.create(
          PLUS, STAR, QUEST
  );

  public static final IElementType DOC = get(CompactSyntaxConstants.DOCUMENTATION);
  public static final TokenSet DOC_TOKENS = TokenSet.create(
          DOC,
          get(CompactSyntaxConstants.DOCUMENTATION_AFTER_SINGLE_LINE_COMMENT),
          get(CompactSyntaxConstants.DOCUMENTATION_CONTINUE)
  );

  public static final IElementType COMMENT = get(CompactSyntaxConstants.SINGLE_LINE_COMMENT);
  public static final TokenSet COMMENTS = TokenSet.create(
          COMMENT,
          get(CompactSyntaxConstants.SINGLE_LINE_COMMENT_CONTINUE)
  );

  public static final IElementType ESCAPED_IDENTIFIER = get(CompactSyntaxConstants.ESCAPED_IDENTIFIER);
  public static final TokenSet IDENTIFIERS = TokenSet.create(
          get(CompactSyntaxConstants.IDENTIFIER),
          ESCAPED_IDENTIFIER
  );

  public static final TokenSet OPERATORS = TokenSet.orSet(TokenSet.create(
          CHOICE_EQ, INTERLEAVE_EQ), BINARY_OPS, QUANTIFIER_OPS);

  public static final IElementType LITERAL = get(CompactSyntaxConstants.LITERAL);
  public static final TokenSet STRINGS = TokenSet.create(LITERAL);

  static final TokenSet READABLE_TEXT = TokenSet.orSet(
          DOC_TOKENS, COMMENTS,
          TokenSet.create(LITERAL));

  public static final IElementType KEYWORD_ElEMENT = get("KEYWORD_ELEMENT");
  public static final IElementType KEYWORD_ATTRIBUTE = get("KEYWORD_ATTRIBUTE");
  public static final IElementType KEYWORD_NAMESPACE = get("KEYWORD_NAMESPACE");
  public static final IElementType KEYWORD_LIST = get("KEYWORD_LIST");
  public static final IElementType KEYWORD_MIXED = get("KEYWORD_MIXED");
  public static final IElementType KEYWORD_EMPTY = get("KEYWORD_EMPTY");
  public static final IElementType KEYWORD_GRAMMAR = get("KEYWORD_GRAMMAR");
  public static final IElementType KEYWORD_TEXT = get("KEYWORD_TEXT");
  public static final IElementType KEYWORD_PARENT = get("KEYWORD_PARENT");
  public static final IElementType KEYWORD_EXTERNAL = get("KEYWORD_EXTERNAL");
  public static final IElementType KEYWORD_NOT_ALLOWED = get("KEYWORD_NOTALLOWED");
  public static final IElementType KEYWORD_START = get("KEYWORD_START");
  public static final IElementType KEYWORD_INCLUDE = get("KEYWORD_INCLUDE");
  public static final IElementType KEYWORD_DEFAULT = get("KEYWORD_DEFAULT");
  public static final IElementType KEYWORD_INHERIT = get("KEYWORD_INHERIT");
  public static final IElementType KEYWORD_STRING = get("KEYWORD_STRING");
  public static final IElementType KEYWORD_TOKEN = get("KEYWORD_TOKEN");
  public static final IElementType KEYWORD_DATATYPES = get("KEYWORD_DATATYPES");
  public static final IElementType KEYWORD_DIV = get("KEYWORD_DIV");

  public static final TokenSet KEYWORDS = TokenSet.create(
          KEYWORD_ATTRIBUTE,
          KEYWORD_DATATYPES,
          KEYWORD_DEFAULT,
          KEYWORD_DIV,
          KEYWORD_ElEMENT,
          KEYWORD_EMPTY,
          KEYWORD_EXTERNAL,
          KEYWORD_GRAMMAR,
          KEYWORD_INCLUDE,
          KEYWORD_INHERIT,
          KEYWORD_LIST,
          KEYWORD_MIXED,
          KEYWORD_NAMESPACE,
          KEYWORD_NOT_ALLOWED,
          KEYWORD_PARENT,
          KEYWORD_START,
          KEYWORD_STRING,
          KEYWORD_TEXT,
          KEYWORD_TOKEN
  );

  public static final TokenSet IDENTIFIER_OR_KEYWORD = TokenSet.orSet(KEYWORDS, IDENTIFIERS);
}
