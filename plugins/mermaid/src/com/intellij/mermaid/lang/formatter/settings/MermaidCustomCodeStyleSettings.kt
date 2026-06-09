// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.formatter.settings

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

@Suppress("PropertyName")
class MermaidCustomCodeStyleSettings(settings: CodeStyleSettings) :
  CustomCodeStyleSettings(MermaidLanguage.id, settings) {

  //region SPACES
  @JvmField
  var FORCE_ONE_SPACE_BETWEEN_WORDS: Boolean = true

  @JvmField
  var AROUND_STYLE_SEPARATOR: Boolean = false

  @JvmField
  var BEFORE_GENERIC: Boolean = false

  @JvmField
  var AROUND_EQUALITY: Boolean = false

  @JvmField
  var BEFORE_COMMA: Boolean = false

  @JvmField
  var AFTER_COMMA: Boolean = true

  @JvmField
  var BEFORE_COLON: Boolean = false

  @JvmField
  var AFTER_COLON: Boolean = true

  @JvmField
  var BEFORE_SEMICOLON: Boolean = false

  @JvmField
  var BEFORE_OPEN_CURLY: Boolean = true

  @JvmField
  var BEFORE_OPEN_ROUND: Boolean = false

  @JvmField
  var WITHIN_ROUND: Boolean = false

  @JvmField
  var WITHIN_SQUARE: Boolean = false

  @JvmField
  var WITHIN_CURLY: Boolean = true

  @JvmField
  var WITHIN_NODE_SHAPES: Boolean = false

  @JvmField
  var BETWEEN_NODE_ID_AND_NODE_SHAPE: Boolean = false

  @JvmField
  var WITHIN_ANNOTATION_BRACES: Boolean = false

  @JvmField
  var BETWEEN_STATE_AND_ANNOTATION: Boolean = true

  @JvmField
  var BEFORE_ARROW_TEXT_WITHIN_SEP: Boolean = false

  @JvmField
  var AFTER_ARROW_TEXT_WITHIN_SEP: Boolean = false

  @JvmField
  var WITHIN_ARROW_TEXT_SEP: Boolean = false

  @JvmField
  var AROUND_INLINE_ARROW_TEXT: Boolean = true

  @JvmField
  var AROUND_ARROW: Boolean = true

  @JvmField
  var BEETWEEN_LINE_TYPE_AND_RELATION_TYPE: Boolean = false
  //endregion

  //region BLANK LINES
  @JvmField
  var MIN_LINES_AROUND_STRUCTURED_STATEMENTS: Int = 0

  @JvmField
  var KEEP_LINES_AROUND_STRUCTURED_STATEMENTS: Int = 1

  @JvmField
  var MIN_LINES_BETWEEN_STRUCTURED_STATEMENTS: Int = 0

  @JvmField
  var KEEP_LINES_BETWEEN_STRUCTURED_STATEMENTS: Int = 1

  @JvmField
  var MIN_LINES_BETWEEN_OTHER_STATEMENTS: Int = 0

  @JvmField
  var KEEP_LINES_BETWEEN_OTHER_STATEMENTS: Int = 0

  @JvmField
  var MIN_LINES_WITHIN_STRUCTURES: Int = 0

  @JvmField
  var KEEP_LINES_WITHIN_STRUCTURES: Int = 0
  //endregion
}
