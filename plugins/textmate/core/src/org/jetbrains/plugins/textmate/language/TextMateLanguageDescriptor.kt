package org.jetbrains.plugins.textmate.language

import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.syntax.InjectionNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor

class TextMateLanguageDescriptor(
  val rootSyntaxNode: SyntaxNodeDescriptor,
  val injections: List<InjectionNodeDescriptor>,
) {
  @Deprecated("Use TextMateLanguageDescriptor(rootSyntaxNode, injections) instead")
  constructor(scopeName: CharSequence, rootSyntaxNode: SyntaxNodeDescriptor) : this(rootSyntaxNode, emptyList())

  val rootScopeName: CharSequence? = rootSyntaxNode.getStringAttribute(Constants.StringKey.SCOPE_NAME)
}
