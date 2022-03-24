// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation

@Suppress("DEPRECATION")
class MarkdownCodeBlock(node: ASTNode): MarkdownCodeBlockImpl(node) {
  override fun getPresentation(): ItemPresentation {
    return object: MarkdownBasePresentation() {
      override fun getPresentableText(): String {
        return "Code block"
      }

      override fun getLocationString(): String? {
        if (!isValid) {
          return null
        }
        val sb = StringBuilder()
        var child = firstChild
        while (child != null) {
          if (child.node.elementType !== MarkdownTokenTypes.CODE_LINE) {
            child = child.nextSibling
            continue
          }
          if (sb.isNotEmpty()) {
            sb.append("\\n")
          }
          sb.append(child.text)
          if (sb.length >= MarkdownCompositePsiElementBase.PRESENTABLE_TEXT_LENGTH) {
            break
          }
          child = child.nextSibling
        }
        return sb.toString()
      }
    }
  }
}
