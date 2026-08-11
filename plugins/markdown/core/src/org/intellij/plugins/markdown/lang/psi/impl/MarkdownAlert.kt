// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode

internal class MarkdownAlert(node: ASTNode) : MarkdownCompositePsiElementBase(node) {
  override fun getPresentableTagName(): String = "alert"
}
