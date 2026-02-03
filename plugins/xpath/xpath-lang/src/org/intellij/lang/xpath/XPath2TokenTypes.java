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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public final class XPath2TokenTypes {
  public static final IElementType COMMENT = new XPathElementType("COMMENT");
  public static final IElementType END_COMMENT = new XPathElementType("END_COMMENT");

  public static final IElementType SOME = new XPathElementType("SOME");
  public static final IElementType EVERY = new XPathElementType("EVERY");
  public static final TokenSet QUANTIFIERS = TokenSet.create(SOME, EVERY);

  public static final IElementType IF = new XPathElementType("IF");
  public static final IElementType THEN = new XPathElementType("THEN");
  public static final IElementType ELSE = new XPathElementType("ELSE");

  public static final IElementType FOR = new XPathElementType("FOR");
  public static final IElementType RETURN = new XPathElementType("RETURN");
  public static final IElementType IN = new XPathElementType("IN");
  public static final IElementType SATISFIES = new XPathElementType("SATISFIES");
  public static final IElementType TO = new XPathElementType("TO");

  public static final IElementType IS = new XPathElementType("IS");

  public static final IElementType BEFORE = new XPathElementType("BEFORE");
  public static final IElementType AFTER = new XPathElementType("AFTER");


  public static final IElementType IDIV = new XPathElementType("IDIV");
  public static final IElementType UNION = new XPathElementType("UNION");
  public static final IElementType QUEST = new XPathElementType("QUEST");

  public static final IElementType INTERSECT = new XPathElementType("INTERSECT");
  public static final IElementType EXCEPT = new XPathElementType("EXCEPT");
  public static final TokenSet INTERSECT_EXCEPT = TokenSet.create(INTERSECT, EXCEPT);

  public static final IElementType INSTANCE = new XPathElementType("INSTANCE");
  public static final IElementType OF = new XPathElementType("OF");
  public static final IElementType TREAT = new XPathElementType("TREAT");
  public static final IElementType AS = new XPathElementType("AS");
  public static final IElementType CAST = new XPathElementType("CAST");
  public static final IElementType CASTABLE = new XPathElementType("CASTABLE");

  public static final IElementType ITEM = new XPathElementType("ITEM");
  public static final IElementType EMPTY_SEQUENCE = new XPathElementType("EMPTY_SEQUENCE");

  public static final IElementType WEQ = new XPathElementType("WEQ");       // =
  public static final IElementType WLE = new XPathElementType("WLE");       // <=
  public static final IElementType WLT = new XPathElementType("WLT");       // <
  public static final IElementType WGT = new XPathElementType("WGT");       // >
  public static final IElementType WGE = new XPathElementType("WGE");       // >=
  public static final IElementType WNE = new XPathElementType("WNE");       // !=
  private static final TokenSet WORD_COMP_OPS = TokenSet.create(WEQ, WLE, WLT, WGT, WGE, WNE);

  public static final TokenSet NODE_COMP_OPS = TokenSet.create(IS, BEFORE, AFTER);
  public static final TokenSet COMP_OPS = TokenSet.orSet(XPathTokenTypes.REL_OPS, XPathTokenTypes.EQUALITY_OPS, WORD_COMP_OPS, NODE_COMP_OPS);
  public static final TokenSet MULT_OPS = TokenSet.orSet(XPathTokenTypes.MUL_OPS, TokenSet.create(IDIV));
  public static final TokenSet UNION_OPS = TokenSet.create(XPathTokenTypes.UNION, UNION);
  public static final TokenSet OCCURRENCE_OPS = TokenSet.create(XPathTokenTypes.PLUS, XPathTokenTypes.STAR, QUEST);

  public static final TokenSet BOOLEAN_OPERATIONS = TokenSet.orSet(XPathTokenTypes.BOOLEAN_OPERATIONS, COMP_OPS);
  public static final TokenSet NUMBER_OPERATIONS = TokenSet.orSet(XPathTokenTypes.NUMBER_OPERATIONS, TokenSet.create(IDIV));


  public static final TokenSet KEYWORDS = TokenSet.orSet(
          WORD_COMP_OPS,
          QUANTIFIERS,
          INTERSECT_EXCEPT,
          TokenSet.create(IF, THEN, ELSE, FOR, RETURN, IN, SATISFIES, TO, IDIV, UNION, IS, INSTANCE, OF, TREAT, AS, CAST, CASTABLE),
          XPathTokenTypes.KEYWORDS);

  private XPath2TokenTypes() {
  }
}
