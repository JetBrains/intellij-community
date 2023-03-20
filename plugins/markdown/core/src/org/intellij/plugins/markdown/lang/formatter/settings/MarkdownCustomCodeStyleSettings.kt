// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.settings

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownLanguage

@Suppress("PropertyName")
class MarkdownCustomCodeStyleSettings(settings: CodeStyleSettings) : CustomCodeStyleSettings(MarkdownLanguage.INSTANCE.id, settings) {
  //BLANK LINES
  // See IDEA-291443
  @JvmField
  //@Property(externalName = "min_lines_around_header")
  var MAX_LINES_AROUND_HEADER: Int = 1

  @JvmField
  //@Property(externalName = "max_lines_around_header")
  var MIN_LINES_AROUND_HEADER: Int = 1


  @JvmField
  //@Property(externalName = "min_lines_around_block_elements")
  var MAX_LINES_AROUND_BLOCK_ELEMENTS: Int = 1

  @JvmField
  //@Property(externalName = "max_lines_around_block_elements")
  var MIN_LINES_AROUND_BLOCK_ELEMENTS: Int = 1


  @JvmField
  //@Property(externalName = "min_lines_between_paragraphs")
  var MAX_LINES_BETWEEN_PARAGRAPHS: Int = 1

  @JvmField
  //@Property(externalName = "max_lines_between_paragraphs")
  var MIN_LINES_BETWEEN_PARAGRAPHS: Int = 1

  //SPACES
  @JvmField
  var FORCE_ONE_SPACE_BETWEEN_WORDS: Boolean = true

  @JvmField
  var FORCE_ONE_SPACE_AFTER_HEADER_SYMBOL: Boolean = true

  @JvmField
  var FORCE_ONE_SPACE_AFTER_LIST_BULLET: Boolean = true

  @JvmField
  var FORCE_ONE_SPACE_AFTER_BLOCKQUOTE_SYMBOL: Boolean = true

  @JvmField
  var WRAP_TEXT_IF_LONG = true

  @JvmField
  var KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS = true

  @JvmField
  var WRAP_TEXT_INSIDE_BLOCKQUOTES = true

  @JvmField
  var INSERT_QUOTE_ARROWS_ON_WRAP = true

  @JvmField
  var FORMAT_TABLES = true
}
