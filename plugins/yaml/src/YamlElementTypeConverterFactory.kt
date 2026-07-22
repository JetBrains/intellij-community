// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.platform.syntax.psi.ElementTypeConverter
import com.intellij.platform.syntax.psi.ElementTypeConverterFactory
import com.intellij.platform.syntax.psi.elementTypeConverterOf
import org.jetbrains.yaml.syntax.YamlSyntaxElementTypes
import org.jetbrains.yaml.syntax.YamlSyntaxTokenTypes

class YamlElementTypeConverterFactory : ElementTypeConverterFactory {
  override fun getElementTypeConverter(): ElementTypeConverter = yamlElementTypeConverter
}

private val yamlElementTypeConverter = elementTypeConverterOf(
  YamlSyntaxElementTypes.DOCUMENT to YAMLElementTypes.DOCUMENT,

  YamlSyntaxElementTypes.KEY_VALUE_PAIR to YAMLElementTypes.KEY_VALUE_PAIR,
  YamlSyntaxElementTypes.HASH to YAMLElementTypes.HASH,
  YamlSyntaxElementTypes.ARRAY to YAMLElementTypes.ARRAY,
  YamlSyntaxElementTypes.SEQUENCE_ITEM to YAMLElementTypes.SEQUENCE_ITEM,
  YamlSyntaxElementTypes.COMPOUND_VALUE to YAMLElementTypes.COMPOUND_VALUE,
  YamlSyntaxElementTypes.MAPPING to YAMLElementTypes.MAPPING,
  YamlSyntaxElementTypes.SEQUENCE to YAMLElementTypes.SEQUENCE,
  YamlSyntaxElementTypes.SCALAR_LIST_VALUE to YAMLElementTypes.SCALAR_LIST_VALUE,
  YamlSyntaxElementTypes.SCALAR_TEXT_VALUE to YAMLElementTypes.SCALAR_TEXT_VALUE,
  YamlSyntaxElementTypes.SCALAR_PLAIN_VALUE to YAMLElementTypes.SCALAR_PLAIN_VALUE,
  YamlSyntaxElementTypes.SCALAR_QUOTED_STRING to YAMLElementTypes.SCALAR_QUOTED_STRING,
  YamlSyntaxElementTypes.ANCHOR_NODE to YAMLElementTypes.ANCHOR_NODE,
  YamlSyntaxElementTypes.ALIAS_NODE to YAMLElementTypes.ALIAS_NODE,

  YamlSyntaxTokenTypes.COMMENT to YAMLTokenTypes.COMMENT,
  YamlSyntaxTokenTypes.WHITESPACE to YAMLTokenTypes.WHITESPACE,
  YamlSyntaxTokenTypes.INDENT to YAMLTokenTypes.INDENT,
  YamlSyntaxTokenTypes.EOL to YAMLTokenTypes.EOL,
  YamlSyntaxTokenTypes.SCALAR_EOL to YAMLTokenTypes.SCALAR_EOL,

  YamlSyntaxTokenTypes.LBRACE to YAMLTokenTypes.LBRACE,
  YamlSyntaxTokenTypes.RBRACE to YAMLTokenTypes.RBRACE,
  YamlSyntaxTokenTypes.LBRACKET to YAMLTokenTypes.LBRACKET,
  YamlSyntaxTokenTypes.RBRACKET to YAMLTokenTypes.RBRACKET,
  YamlSyntaxTokenTypes.COMMA to YAMLTokenTypes.COMMA,
  YamlSyntaxTokenTypes.COLON to YAMLTokenTypes.COLON,
  YamlSyntaxTokenTypes.QUESTION to YAMLTokenTypes.QUESTION,
  YamlSyntaxTokenTypes.AMPERSAND to YAMLTokenTypes.AMPERSAND,
  YamlSyntaxTokenTypes.STAR to YAMLTokenTypes.STAR,

  YamlSyntaxTokenTypes.DOCUMENT_MARKER to YAMLTokenTypes.DOCUMENT_MARKER,
  YamlSyntaxTokenTypes.DOCUMENT_END to YAMLTokenTypes.DOCUMENT_END,
  YamlSyntaxTokenTypes.SEQUENCE_MARKER to YAMLTokenTypes.SEQUENCE_MARKER,

  YamlSyntaxTokenTypes.TAG to YAMLTokenTypes.TAG,

  YamlSyntaxTokenTypes.SCALAR_KEY to YAMLTokenTypes.SCALAR_KEY,
  YamlSyntaxTokenTypes.TEXT to YAMLTokenTypes.TEXT,

  YamlSyntaxTokenTypes.SCALAR_STRING to YAMLTokenTypes.SCALAR_STRING,
  YamlSyntaxTokenTypes.SCALAR_DSTRING to YAMLTokenTypes.SCALAR_DSTRING,

  YamlSyntaxTokenTypes.SCALAR_LIST to YAMLTokenTypes.SCALAR_LIST,
  YamlSyntaxTokenTypes.SCALAR_TEXT to YAMLTokenTypes.SCALAR_TEXT,

  YamlSyntaxTokenTypes.ANCHOR to YAMLTokenTypes.ANCHOR,
  YamlSyntaxTokenTypes.ALIAS to YAMLTokenTypes.ALIAS,
)

class YamlFileElementTypeConverterFactory : ElementTypeConverterFactory {
  override fun getElementTypeConverter(): ElementTypeConverter = yamlFileElementTypeConverter
}

private val yamlFileElementTypeConverter = elementTypeConverterOf(
  YamlSyntaxElementTypes.FILE to YAML_FILE
)

