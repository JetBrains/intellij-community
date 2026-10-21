// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ast.impl

import com.intellij.lang.ASTFactory
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import com.jetbrains.python.PyPsiElementFactory
import com.jetbrains.python.PyTokenTypes
import org.jetbrains.annotations.ApiStatus

/** Creates the string leaves of the Python AST through [PyPsiElementFactory]. */
@ApiStatus.Internal
class PythonASTFactory : ASTFactory() {
  override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {
    if (PyTokenTypes.STRING_NODES.contains(type)) {
      return PyPsiElementFactory.current().createStringLeaf(type, text)
    }
    return super.createLeaf(type, text)
  }
}
