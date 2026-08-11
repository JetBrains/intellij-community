package com.intellij.yaml.syntax

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.flattenSyntaxElementTypeSets
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import kotlin.jvm.JvmField

object YamlSyntaxElementTypes {
  @JvmField val FILE: SyntaxElementType = SyntaxElementType("YAML_FILE")
  @JvmField val DOCUMENT: SyntaxElementType = SyntaxElementType("Document ---")

  @JvmField val KEY_VALUE_PAIR: SyntaxElementType = SyntaxElementType("Key value pair")

  @JvmField val HASH: SyntaxElementType = SyntaxElementType("Hash")
  @JvmField val ARRAY: SyntaxElementType = SyntaxElementType("Array")
  @JvmField val SEQUENCE_ITEM: SyntaxElementType = SyntaxElementType("Sequence item")
  @JvmField val COMPOUND_VALUE: SyntaxElementType = SyntaxElementType("Compound value")
  @JvmField val MAPPING: SyntaxElementType = SyntaxElementType("Mapping")
  @JvmField val SEQUENCE: SyntaxElementType = SyntaxElementType("Sequence")
  @JvmField val SCALAR_LIST_VALUE: SyntaxElementType = SyntaxElementType("Scalar list value")
  @JvmField val SCALAR_TEXT_VALUE: SyntaxElementType = SyntaxElementType("Scalar text value")
  @JvmField val SCALAR_PLAIN_VALUE: SyntaxElementType = SyntaxElementType("Scalar plain style")
  @JvmField val SCALAR_QUOTED_STRING: SyntaxElementType = SyntaxElementType("Scalar quoted string")
  @JvmField val ANCHOR_NODE: SyntaxElementType = SyntaxElementType("Anchor node")
  @JvmField val ALIAS_NODE: SyntaxElementType = SyntaxElementType("Alias node")

  @JvmField
  val BLOCK_SCALAR_ITEMS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    YamlSyntaxTokenTypes.SCALAR_LIST,
    YamlSyntaxTokenTypes.SCALAR_TEXT
  )

  @JvmField
  val SCALAR_ITEMS: SyntaxElementTypeSet = flattenSyntaxElementTypeSets(
    BLOCK_SCALAR_ITEMS, syntaxElementTypeSetOf(
      YamlSyntaxTokenTypes.SCALAR_STRING,
      YamlSyntaxTokenTypes.SCALAR_DSTRING,
      YamlSyntaxTokenTypes.TEXT
    )
  )

  @JvmField
  val SCALAR_VALUES: SyntaxElementTypeSet = flattenSyntaxElementTypeSets(
    SCALAR_ITEMS, syntaxElementTypeSetOf(
      SCALAR_LIST_VALUE
    )
  )

  @JvmField
  val EOL_ELEMENTS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    YamlSyntaxTokenTypes.EOL,
    YamlSyntaxTokenTypes.SCALAR_EOL
  )

  @JvmField
  val SPACE_ELEMENTS: SyntaxElementTypeSet = flattenSyntaxElementTypeSets(
    EOL_ELEMENTS, syntaxElementTypeSetOf(
      YamlSyntaxTokenTypes.WHITESPACE,
      SyntaxTokenTypes.WHITE_SPACE,
      YamlSyntaxTokenTypes.INDENT
    )
  )

  @JvmField
  val BLANK_ELEMENTS: SyntaxElementTypeSet = flattenSyntaxElementTypeSets(
    SPACE_ELEMENTS, syntaxElementTypeSetOf(
      YamlSyntaxTokenTypes.COMMENT
    )
  )

  @JvmField
  val CONTAINERS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    SCALAR_LIST_VALUE,
    SCALAR_TEXT_VALUE,
    DOCUMENT,
    SEQUENCE,
    MAPPING,
    SCALAR_QUOTED_STRING,
    SCALAR_PLAIN_VALUE
  )

  @JvmField
  val BRACKETS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    YamlSyntaxTokenTypes.LBRACE,
    YamlSyntaxTokenTypes.RBRACE,
    YamlSyntaxTokenTypes.LBRACKET,
    YamlSyntaxTokenTypes.RBRACKET
  )

  @JvmField
  val DOCUMENT_BRACKETS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    YamlSyntaxTokenTypes.DOCUMENT_MARKER,
    YamlSyntaxTokenTypes.DOCUMENT_END
  )

  @JvmField
  val TOP_LEVEL: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    FILE,
    DOCUMENT
  )

  @JvmField
  val INCOMPLETE_BLOCKS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    MAPPING,
    SEQUENCE,
    COMPOUND_VALUE,
    SCALAR_LIST_VALUE,
    SCALAR_TEXT_VALUE
  )
}