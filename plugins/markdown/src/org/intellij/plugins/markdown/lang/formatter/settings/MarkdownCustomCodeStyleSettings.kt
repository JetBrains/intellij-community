// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.settings

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownLanguage

@Suppress("PropertyName")
class MarkdownCustomCodeStyleSettings(settings: CodeStyleSettings) : CustomCodeStyleSettings(MarkdownLanguage.INSTANCE.id, settings) {
  //BLANK LINES
  @JvmField
  var MAX_LINES_AROUND_HEADER: Int = 1

  @JvmField
  var MIN_LINES_AROUND_HEADER: Int = 1


  @JvmField
  var MAX_LINES_AROUND_BLOCK_ELEMENTS: Int = 1

  @JvmField
  var MIN_LINES_AROUND_BLOCK_ELEMENTS: Int = 1


  @JvmField
  var MAX_LINES_BETWEEN_PARAGRAPHS: Int = 1

  @JvmField
  var MIN_LINES_BETWEEN_PARAGRAPHS: Int = 1

  //SPACES
  @JvmField
  var FORCE_ONE_SPACE_AFTER_HEADER_SYMBOL: Boolean = true
  @JvmField
  var FORCE_ONE_SPACE_AFTER_LIST_BULLET: Boolean = true
  @JvmField
  var FORCE_ONE_SPACE_AFTER_BLOCKQUOTE_SYMBOL: Boolean = true
}