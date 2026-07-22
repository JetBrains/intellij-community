package org.jetbrains.yaml.syntax

import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import org.jetbrains.yaml.syntax.lexer.YamlLexer

object YamlSyntaxDefinition : LanguageSyntaxDefinition {
  override fun parse(builder: SyntaxTreeBuilder) {
    YamlParser(builder).parse()
  }

  override fun createLexer(): Lexer = YamlLexer()

  override val comments: SyntaxElementTypeSet = syntaxElementTypeSetOf(YamlSyntaxTokenTypes.COMMENT)
  override val whitespaces: SyntaxElementTypeSet = syntaxElementTypeSetOf(YamlSyntaxTokenTypes.WHITESPACE)
}