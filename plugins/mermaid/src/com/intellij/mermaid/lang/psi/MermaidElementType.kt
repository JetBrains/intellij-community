package com.intellij.mermaid.lang.psi

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.psi.tree.IElementType

class MermaidElementType(name: String): IElementType(name, MermaidLanguage)
