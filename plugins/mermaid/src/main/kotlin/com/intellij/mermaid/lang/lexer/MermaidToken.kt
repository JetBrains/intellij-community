package com.intellij.mermaid.lang.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.mermaid.lang.MermaidLanguage

class MermaidToken(debugName: String): IElementType(debugName, MermaidLanguage)
