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
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

public final class XPath2ElementTypes {
  public static final IFileElementType FILE = new IFileElementType("XPATH2_FILE", XPathFileType.XPATH2.getLanguage());

  public static final IElementType SEQUENCE = new XPath2ElementType("SEQUENCE");
  public static final IElementType RANGE_EXPRESSION = new XPath2ElementType("RANGE_EXPRESSION");
  public static final IElementType IF = new XPath2ElementType("IF");
  public static final IElementType FOR = new XPath2ElementType("FOR");
  public static final IElementType BODY = new XPath2ElementType("RETURN");
  public static final IElementType QUANTIFIED = new XPath2ElementType("QUANTIFIED");

  public static final IElementType INSTANCE_OF = new XPath2ElementType("INSTANCE_OF");
  public static final IElementType TREAT_AS = new XPath2ElementType("TREAT_AS");
  public static final IElementType CASTABLE_AS = new XPath2ElementType("CASTABLE_AS");
  public static final IElementType CAST_AS = new XPath2ElementType("CAST_AS");

  public static final IElementType BINDING_SEQ = new XPath2ElementType("BINDING_SEQ");

  public static final IElementType VARIABLE_DECL = new XPath2ElementType("VARIABLE_DECL");
  public static final IElementType CONTEXT_ITEM = new XPath2ElementType("CONTEXT_ITEM");

  public static final IElementType SEQUENCE_TYPE = new XPath2ElementType("SEQUENCE_TYPE");
  public static final IElementType SINGLE_TYPE = new XPath2ElementType("SINGLE_TYPE");
  public static final IElementType ITEM_OR_EMPTY_SEQUENCE = new XPath2ElementType("ITEM_OR_EMPTY_SEQUENCE");
  public static final TokenSet TYPE_ELEMENTS = TokenSet.create(SEQUENCE_TYPE, SINGLE_TYPE);

  public static final TokenSet EXPRESSIONS = TokenSet.orSet(
          XPathElementTypes.EXPRESSIONS,
          TokenSet.create(XPathElementTypes.NODE_TYPE, SEQUENCE, CONTEXT_ITEM, IF, FOR, RANGE_EXPRESSION, QUANTIFIED, INSTANCE_OF, TREAT_AS, CASTABLE_AS, CAST_AS)
  );

  private XPath2ElementTypes() {
  }
}
