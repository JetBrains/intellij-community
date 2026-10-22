// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.IElementType
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.psi.PyPsiCreatingElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@Subsystems.Parsing
@Layers.Functional
class PythonParserDefinitionTest {
  /** A dialect element type that is not a [com.jetbrains.python.psi.PyElementType], like the legacy stub element type of a dialect. */
  private class DialectElementType : IElementType("DIALECT_ELEMENT", PythonLanguage.INSTANCE), PyPsiCreatingElementType {
    override fun createElement(node: ASTNode): PsiElement = DialectPsiElement(node)
  }

  private class DialectPsiElement(node: ASTNode) : ASTWrapperPsiElement(node)

  @Test
  fun `createElement calls a PSI-creating element type of a dialect`() {
    val node = CompositeElement(DialectElementType())
    assertThat(PythonParserDefinition().createElement(node)).isInstanceOf(DialectPsiElement::class.java)
  }

  @Test
  fun `createElement wraps an unknown element type`() {
    val node = CompositeElement(IElementType("UNKNOWN_ELEMENT", PythonLanguage.INSTANCE))
    assertThat(PythonParserDefinition().createElement(node)).isExactlyInstanceOf(ASTWrapperPsiElement::class.java)
  }
}
