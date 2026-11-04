// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("YamlInjectedBlockFactory")
package org.jetbrains.yaml.formatter

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.IndentImpl
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.MultiFragmentInjectedLanguageBlockBuilder
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.util.SmartList
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage


internal fun substituteInjectedBlocks(settings: CodeStyleSettings,
                             rawSubBlocks: List<Block>,
                             injectionHost: ASTNode,
                             wrap: Wrap?,
                             alignment: Alignment?): List<Block> {
  val injectedBlocks = SmartList<Block>().apply {
    val outerBLocks = rawSubBlocks.filter { (it as? ASTBlock)?.node is OuterLanguageElement }
    val fixedIndent = IndentImpl(Indent.Type.SPACES, false, settings.getIndentSize(YAMLFileType.YML), false, false)
    MultiFragmentInjectedLanguageBlockBuilder(settings, outerBLocks, YAMLLanguage.INSTANCE)
      .addInjectedBlocks(this, injectionHost, wrap, alignment, fixedIndent)
  }
  if (injectedBlocks.isEmpty()) return rawSubBlocks

  injectedBlocks.addAll(0,
    rawSubBlocks.filter(injectedBlocks.first().textRange.startOffset.let { start -> { it.textRange.endOffset <= start } }))
  injectedBlocks.addAll(rawSubBlocks.filter(injectedBlocks.last().textRange.endOffset.let { end -> { it.textRange.startOffset >= end } }))

  return injectedBlocks
}
