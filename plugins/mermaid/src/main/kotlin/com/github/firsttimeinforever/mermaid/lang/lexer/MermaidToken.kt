package com.github.firsttimeinforever.mermaid.lang.lexer

import com.intellij.psi.tree.IElementType
import com.github.firsttimeinforever.mermaid.lang.MermaidLanguage

class MermaidToken(debugName: String): IElementType(debugName, MermaidLanguage)
