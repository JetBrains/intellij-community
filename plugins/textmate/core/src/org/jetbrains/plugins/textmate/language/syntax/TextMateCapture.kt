package org.jetbrains.plugins.textmate.language.syntax

sealed class TextMateCapture {
  class Name(val name: CharSequence): TextMateCapture()
  class Rule(val node: SyntaxNodeDescriptor): TextMateCapture()
}
