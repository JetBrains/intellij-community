package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.psi.tree.IElementType

class MermaidToken(debugName: String): IElementType(debugName, MermaidLanguage)
