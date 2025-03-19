package org.jetbrains.yaml;

/**
 * @author oleg
 */
public interface YAMLTokenTypes {
  YAMLElementType COMMENT = new YAMLElementType("comment");
  YAMLElementType WHITESPACE = new YAMLElementType("whitespace");
  YAMLElementType INDENT = new YAMLElementType("indent");
  YAMLElementType EOL = new YAMLElementType("Eol");
  YAMLElementType SCALAR_EOL = new YAMLElementType("block scalar EOL");

  YAMLElementType LBRACE = new YAMLElementType("{");
  YAMLElementType RBRACE = new YAMLElementType("}");
  YAMLElementType LBRACKET = new YAMLElementType("[");
  YAMLElementType RBRACKET = new YAMLElementType("]");
  YAMLElementType COMMA = new YAMLElementType(",");
  YAMLElementType COLON = new YAMLElementType(":");
  YAMLElementType QUESTION = new YAMLElementType("?");
  YAMLElementType AMPERSAND = new YAMLElementType("&");
  YAMLElementType STAR = new YAMLElementType("*");

  YAMLElementType DOCUMENT_MARKER = new YAMLElementType("---");
  YAMLElementType DOCUMENT_END = new YAMLElementType("...");
  YAMLElementType SEQUENCE_MARKER = new YAMLElementType("-");

  YAMLElementType TAG = new YAMLElementType("tag");

  YAMLElementType SCALAR_KEY = new YAMLElementType("scalar key");
  // sequential TEXT tokens will merge for parser into one token
  YAMLElementType TEXT = new YAMLElementType("text");

  YAMLElementType SCALAR_STRING = new YAMLElementType("scalar string");
  YAMLElementType SCALAR_DSTRING = new YAMLElementType("scalar dstring");

  YAMLElementType SCALAR_LIST = new YAMLElementType("scalar list");
  YAMLElementType SCALAR_TEXT = new YAMLElementType("scalar text");

  YAMLElementType ANCHOR = new YAMLElementType("anchor");
  YAMLElementType ALIAS = new YAMLElementType("alias");
}
