// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.yaml.syntax.lexer

import com.intellij.platform.syntax.util.lexer.FlexAdapter

internal class YamlFlexAdapter(private val yamlFlex: YamlFlexLexer) : FlexAdapter(yamlFlex) {
  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    if (!YamlLexerStates.isRestartable(initialState) && initialState != YamlLexerStates.DIRTY_STATE) {
      yamlFlex.lexer.cleanMyState()
    }
    super.start(buffer, startOffset, endOffset, initialState)
  }
}
