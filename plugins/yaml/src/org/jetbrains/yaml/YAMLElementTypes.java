package org.jetbrains.yaml;

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

  TokenSet SCALAR_VALUES = TokenSet.create(
    YAMLTokenTypes.SCALAR_TEXT,
    YAMLTokenTypes.SCALAR_STRING,
    YAMLTokenTypes.SCALAR_DSTRING,
    YAMLTokenTypes.SCALAR_LIST,
    YAMLTokenTypes.TEXT,
    SCALAR_LIST_VALUE
  );
}
