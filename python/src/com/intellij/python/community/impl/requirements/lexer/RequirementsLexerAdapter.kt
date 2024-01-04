// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.requirements.lexer

import com.intellij.lexer.FlexAdapter

class RequirementsLexerAdapter: FlexAdapter(
  com.intellij.python.community.impl.requirements.lexer.RequirementsLexer(null)) {
}