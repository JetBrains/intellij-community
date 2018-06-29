package org.jetbrains.yaml;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author oleg
 */
public interface YAMLElementTypes {
  IFileElementType FILE = new IFileElementType(YAMLLanguage.INSTANCE);

  YAMLElementType DOCUMENT = new YAMLElementType("Document ---");

  YAMLElementType KEY_VALUE_PAIR = new YAMLElementType("Key value pair");
  //YAMLElementType VALUE = new YAMLElementType("Value");
  YAMLElementType HASH = new YAMLElementType("Hash");
  YAMLElementType ARRAY = new YAMLElementType("Array");
  YAMLElementType SEQUENCE_ITEM = new YAMLElementType("Sequence item");
  YAMLElementType COMPOUND_VALUE = new YAMLElementType("Compound value");
  YAMLElementType MAPPING = new YAMLElementType("Mapping");
  YAMLElementType SEQUENCE = new YAMLElementType("Sequence");
  YAMLElementType SCALAR_LIST_VALUE = new YAMLElementType("Scalar list value");
  YAMLElementType SCALAR_TEXT_VALUE = new YAMLElementType("Scalar text value");
  YAMLElementType SCALAR_PLAIN_VALUE = new YAMLElementType("Scalar plain style");
  YAMLElementType SCALAR_QUOTED_STRING = new YAMLElementType("Scalar quoted string");
  YAMLElementType ANCHOR_NODE = new YAMLElementType("Anchor node");
  YAMLElementType ALIAS_NODE = new YAMLElementType("Alias node");

  TokenSet BLOCK_SCALAR_ITEMS = TokenSet.create(
    YAMLTokenTypes.SCALAR_LIST,
    YAMLTokenTypes.SCALAR_TEXT
  );

  TokenSet SCALAR_ITEMS = TokenSet.orSet(BLOCK_SCALAR_ITEMS, TokenSet.create(
    YAMLTokenTypes.SCALAR_STRING,
    YAMLTokenTypes.SCALAR_DSTRING,
    YAMLTokenTypes.TEXT
  ));

  TokenSet SCALAR_VALUES = TokenSet.orSet(SCALAR_ITEMS, TokenSet.create(
    SCALAR_LIST_VALUE
  ));

  TokenSet EOL_ELEMENTS = TokenSet.create(
    YAMLTokenTypes.EOL,
    YAMLTokenTypes.SCALAR_EOL
  );

  TokenSet SPACE_ELEMENTS = TokenSet.orSet(EOL_ELEMENTS, TokenSet.create(
    YAMLTokenTypes.WHITESPACE,
    TokenType.WHITE_SPACE,
    YAMLTokenTypes.INDENT
  ));

  TokenSet BLANK_ELEMENTS = TokenSet.orSet(SPACE_ELEMENTS, TokenSet.create(
    YAMLTokenTypes.COMMENT
  ));

  TokenSet CONTAINERS = TokenSet.create(
    SCALAR_LIST_VALUE,
    SCALAR_TEXT_VALUE,
    DOCUMENT,
    SEQUENCE,
    MAPPING,
    SCALAR_QUOTED_STRING,
    SCALAR_PLAIN_VALUE
  );

  TokenSet BRACKETS = TokenSet.create(
    YAMLTokenTypes.LBRACE,
    YAMLTokenTypes.RBRACE,
    YAMLTokenTypes.LBRACKET,
    YAMLTokenTypes.RBRACKET
  );

  TokenSet DOCUMENT_BRACKETS = TokenSet.create(
    YAMLTokenTypes.DOCUMENT_MARKER,
    YAMLTokenTypes.DOCUMENT_END
  );

  TokenSet TOP_LEVEL = TokenSet.create(
    FILE,
    DOCUMENT
  );
}
