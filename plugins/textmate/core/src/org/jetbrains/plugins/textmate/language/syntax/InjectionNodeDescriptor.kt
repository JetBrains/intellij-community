package org.jetbrains.plugins.textmate.language.syntax

data class InjectionNodeDescriptor(
  val selector: String,
  val syntaxNodeDescriptor: SyntaxNodeDescriptor,
)
