package com.intellij.yaml.syntax

import com.intellij.platform.syntax.SyntaxElementType
import kotlin.jvm.JvmField

object YamlSyntaxTokenTypes {
  @JvmField val COMMENT: SyntaxElementType = SyntaxElementType("comment")
  @JvmField val WHITESPACE: SyntaxElementType = SyntaxElementType("whitespace")
  @JvmField val INDENT: SyntaxElementType = SyntaxElementType("indent")
  @JvmField val EOL: SyntaxElementType = SyntaxElementType("Eol")
  @JvmField val SCALAR_EOL: SyntaxElementType = SyntaxElementType("block scalar EOL")

  @JvmField val LBRACE: SyntaxElementType = SyntaxElementType("{")
  @JvmField val RBRACE: SyntaxElementType = SyntaxElementType("}")
  @JvmField val LBRACKET: SyntaxElementType = SyntaxElementType("[")
  @JvmField val RBRACKET: SyntaxElementType = SyntaxElementType("]")
  @JvmField val COMMA: SyntaxElementType = SyntaxElementType(",")
  @JvmField val COLON: SyntaxElementType = SyntaxElementType(":")
  @JvmField val QUESTION: SyntaxElementType = SyntaxElementType("?")
  @JvmField val AMPERSAND: SyntaxElementType = SyntaxElementType("&")
  @JvmField val STAR: SyntaxElementType = SyntaxElementType("*")

  @JvmField val DOCUMENT_MARKER: SyntaxElementType = SyntaxElementType("---")
  @JvmField val DOCUMENT_END: SyntaxElementType = SyntaxElementType("...")
  @JvmField val SEQUENCE_MARKER: SyntaxElementType = SyntaxElementType("-")

  @JvmField val TAG: SyntaxElementType = SyntaxElementType("tag")

  @JvmField val SCALAR_KEY: SyntaxElementType = SyntaxElementType("scalar key")

  // sequential TEXT tokens will merge for parser into one token
  @JvmField val TEXT: SyntaxElementType = SyntaxElementType("text")

  @JvmField val SCALAR_STRING: SyntaxElementType = SyntaxElementType("scalar string")
  @JvmField val SCALAR_DSTRING: SyntaxElementType = SyntaxElementType("scalar dstring")

  @JvmField val SCALAR_LIST: SyntaxElementType = SyntaxElementType("scalar list")
  @JvmField val SCALAR_TEXT: SyntaxElementType = SyntaxElementType("scalar text")

  @JvmField val ANCHOR: SyntaxElementType = SyntaxElementType("anchor")
  @JvmField val ALIAS: SyntaxElementType = SyntaxElementType("alias")
}