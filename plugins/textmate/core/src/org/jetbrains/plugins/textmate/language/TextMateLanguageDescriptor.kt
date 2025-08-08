package org.jetbrains.plugins.textmate.language

import org.jetbrains.plugins.textmate.language.syntax.InjectionNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor

class TextMateLanguageDescriptor(
  val scopeName: CharSequence,
  val rootSyntaxNode: SyntaxNodeDescriptor,
  val injections: List<InjectionNodeDescriptor>,
) {
  @Deprecated("Use TextMateLanguageDescriptor(scopeName, rootSyntaxNode, injections) instead")
  constructor(scopeName: CharSequence, rootSyntaxNode: SyntaxNodeDescriptor) : this(scopeName, rootSyntaxNode, emptyList())
}
