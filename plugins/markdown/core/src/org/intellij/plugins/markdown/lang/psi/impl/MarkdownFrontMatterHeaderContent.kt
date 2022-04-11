package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement

class MarkdownFrontMatterHeaderContent(type: IElementType, text: CharSequence): LeafPsiElement(type, text), MarkdownPsiElement
