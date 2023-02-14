package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.stubs.MarkdownStubBasedPsiElementBase
import org.intellij.plugins.markdown.lang.stubs.MarkdownStubElement
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElement
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElementType

@Deprecated("Please use {@link MarkdownHeader} instead.", ReplaceWith("MarkdownHeader"))
abstract class MarkdownHeaderImpl: MarkdownStubBasedPsiElementBase<MarkdownStubElement<*>>, PsiExternalReferenceHost {
  constructor(node: ASTNode) : super(node)
  constructor(stub: MarkdownHeaderStubElement, type: MarkdownHeaderStubElementType) : super(stub, type)

  @Deprecated("Use level instead.", ReplaceWith("level"))
  val headerNumber
    get() = calculateHeaderLevel()

  protected fun calculateHeaderLevel(): Int {
    val type = node.elementType
    return when {
      MarkdownTokenTypeSets.HEADER_LEVEL_1_SET.contains(type) -> 1
      MarkdownTokenTypeSets.HEADER_LEVEL_2_SET.contains(type) -> 2
      type == MarkdownElementTypes.ATX_3 -> 3
      type == MarkdownElementTypes.ATX_4 -> 4
      type == MarkdownElementTypes.ATX_5 -> 5
      type == MarkdownElementTypes.ATX_6 -> 6
      else -> throw IllegalStateException("Type should be one of header types")
    }
  }
}
