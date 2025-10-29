// This is a generated file. Not intended for manual editing.
package org.toml.lang.psi;

import com.intellij.psi.tree.IElementType;

public interface TomlElementTypes {

  IElementType ARRAY = new TomlCompositeType("ARRAY");
  IElementType ARRAY_TABLE = new TomlCompositeType("ARRAY_TABLE");
  IElementType INLINE_TABLE = new TomlCompositeType("INLINE_TABLE");
  IElementType KEY = new TomlCompositeType("KEY");
  IElementType KEY_SEGMENT = new TomlCompositeType("KEY_SEGMENT");
  IElementType KEY_VALUE = new TomlCompositeType("KEY_VALUE");
  IElementType LITERAL = new TomlCompositeType("LITERAL");
  IElementType TABLE = new TomlCompositeType("TABLE");
  IElementType TABLE_HEADER = new TomlCompositeType("TABLE_HEADER");

  IElementType BARE_KEY = new TomlTokenType("BARE_KEY");
  IElementType BARE_KEY_OR_BOOLEAN = new TomlTokenType("BARE_KEY_OR_BOOLEAN");
  IElementType BARE_KEY_OR_DATE = new TomlTokenType("BARE_KEY_OR_DATE");
  IElementType BARE_KEY_OR_NUMBER = new TomlTokenType("BARE_KEY_OR_NUMBER");
  IElementType BASIC_STRING = new TomlTokenType("BASIC_STRING");
  IElementType BOOLEAN = new TomlTokenType("BOOLEAN");
  IElementType COMMA = new TomlTokenType(",");
  IElementType COMMENT = new TomlTokenType("COMMENT");
  IElementType DATE_TIME = new TomlTokenType("DATE_TIME");
  IElementType DOT = new TomlTokenType(".");
  IElementType EQ = new TomlTokenType("=");
  IElementType LITERAL_STRING = new TomlTokenType("LITERAL_STRING");
  IElementType L_BRACKET = new TomlTokenType("[");
  IElementType L_CURLY = new TomlTokenType("{");
  IElementType MULTILINE_BASIC_STRING = new TomlTokenType("MULTILINE_BASIC_STRING");
  IElementType MULTILINE_LITERAL_STRING = new TomlTokenType("MULTILINE_LITERAL_STRING");
  IElementType NUMBER = new TomlTokenType("NUMBER");
  IElementType R_BRACKET = new TomlTokenType("]");
  IElementType R_CURLY = new TomlTokenType("}");
}
