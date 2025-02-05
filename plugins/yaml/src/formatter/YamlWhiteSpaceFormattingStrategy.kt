// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter

import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.AbstractWhiteSpaceFormattingStrategy
import com.intellij.util.SmartList
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.settingsSync.shouldDoNothingInBackendMode

private class YamlWhiteSpaceFormattingStrategy : AbstractWhiteSpaceFormattingStrategy() {
  override fun check(text: CharSequence, start: Int, end: Int): Int = start

  override fun adjustWhiteSpaceIfNecessary(whiteSpaceText: CharSequence,
                                           text: CharSequence,
                                           startOffset: Int,
                                           endOffset: Int,
                                           codeStyleSettings: CodeStyleSettings?,
                                           nodeAfter: ASTNode?): CharSequence {
    if (shouldDoNothingInBackendMode()) return whiteSpaceText

    if (YAMLTokenTypes.SEQUENCE_MARKER == nodeAfter?.elementType) return whiteSpaceText
    val lineBreaksPositions = whiteSpaceText.indices.filterTo(SmartList()) { whiteSpaceText[it] == '\n' }
      .also { it.add(whiteSpaceText.length) }
    val split = lineBreaksPositions.zipWithNext(whiteSpaceText::subSequence)
    if (split.size <= 1 || split.none { it.length == 1 }) return whiteSpaceText
    val withIndent = split.asSequence().filter { it.length > 1 }.minByOrNull { it.length } ?: return whiteSpaceText
    return split.asSequence().map { if (it.length == 1) withIndent else it }.joinToString("")
  }

}