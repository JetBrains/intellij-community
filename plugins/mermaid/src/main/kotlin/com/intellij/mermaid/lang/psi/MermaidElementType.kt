package com.intellij.mermaid.lang.psi

import com.intellij.psi.tree.IElementType
import com.intellij.mermaid.lang.MermaidLanguage

class MermaidElementType(name: String): IElementType(name, MermaidLanguage)
