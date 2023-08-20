package com.intellij.mermaid.lang.psi

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

open class MermaidLeafPsiElement(type: IElementType, text: CharSequence): LeafPsiElement(type, text), MermaidPsiElement
