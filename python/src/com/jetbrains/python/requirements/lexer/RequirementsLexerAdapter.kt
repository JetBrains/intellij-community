// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.lexer

import com.intellij.lexer.FlexAdapter
import com.jetbrains.python.requirements.RequirementsLexer

class RequirementsLexerAdapter : FlexAdapter(RequirementsLexer(null))