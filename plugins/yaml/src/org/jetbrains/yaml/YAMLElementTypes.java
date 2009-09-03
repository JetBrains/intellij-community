package org.jetbrains.yaml;

import com.intellij.psi.tree.IFileElementType;

/**
 * @author oleg
 */
public interface YAMLElementTypes {
  IFileElementType FILE = new IFileElementType(YAMLLanguage.INSTANCE);

  YAMLElementType DOCUMENT = new YAMLElementType("Document ---");

  YAMLElementType KEY_VALUE_PAIR = new YAMLElementType("Key value pair");
  YAMLElementType HASH = new YAMLElementType("Hash");
  YAMLElementType ARRAY = new YAMLElementType("Array");
  YAMLElementType SEQUENCE = new YAMLElementType("Sequence");
  YAMLElementType COMPOUND_VALUE = new YAMLElementType("Compound value");
  YAMLElementType SCALAR_LIST_VALUE = new YAMLElementType("Scalar list value");
  YAMLElementType SCALAR_TEXT_VALUE = new YAMLElementType("Scalar text value");
}
