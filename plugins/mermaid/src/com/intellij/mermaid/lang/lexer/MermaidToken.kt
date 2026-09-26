// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.psi.tree.IElementType

class MermaidToken(debugName: String): IElementType(debugName, MermaidLanguage)
