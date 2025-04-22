// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.jcef

import com.intellij.openapi.project.Project
import org.intellij.markdown.ast.ASTNode

class MarkdownASTNode(source: ASTNode, val project: Project?, val language: String?) : ASTNode {
  override val type = source.type
  override val startOffset = source.startOffset
  override val endOffset = source.endOffset
  override val parent = source.parent
  override val children = source.children
}
