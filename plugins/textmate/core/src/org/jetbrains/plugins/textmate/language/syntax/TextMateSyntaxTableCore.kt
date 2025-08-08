package org.jetbrains.plugins.textmate.language.syntax

import org.jetbrains.plugins.textmate.getLogger
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.logging.TextMateLogger

/**
 * Table of textmate language descriptors {@link TextMateLanguageDescriptor}.
 *
 * To lex some file you should retrieve the language descriptor
 * by scope name of the target language {@link #getLanguageDescriptor(CharSequence)} and use its root syntax node.
 *
 * Scope name of the target language can be found in syntax files of TextMate bundles.
 */
class TextMateSyntaxTableCore(private val languageDescriptors: Map<CharSequence, TextMateLanguageDescriptor>) {
  companion object {
    private val LOG: TextMateLogger = getLogger(TextMateSyntaxTableCore::class)
  }

  private var rulesRepository: Array<SyntaxNodeDescriptor?>? = null

  fun getLanguageDescriptor(scopeName: CharSequence): TextMateLanguageDescriptor {
    return languageDescriptors[scopeName] ?: TextMateLanguageDescriptor(scopeName, SyntaxNodeDescriptor.EMPTY_NODE, emptyList())
  }

  /**
   * Returns root syntax rule by scope name.
   *
   * @param scopeName Name of scope defined for some language.
   * @return root syntax rule from table for language with a given scope name.
   * If tables don't contain syntax rule for a given scope, the
   * method returns {@link SyntaxNodeDescriptor#EMPTY_NODE}.
   */
  fun getSyntax(scopeName: CharSequence): SyntaxNodeDescriptor {
    val syntaxNodeDescriptor = languageDescriptors[scopeName]?.rootSyntaxNode
    if (syntaxNodeDescriptor == null) {
      LOG.info { "Can't find syntax node for scope: '$scopeName'" }
      return SyntaxNodeDescriptor.EMPTY_NODE
    }
    return syntaxNodeDescriptor
  }

  internal fun setRulesRepository(rulesRepository: Array<SyntaxNodeDescriptor?>) {
    this.rulesRepository = rulesRepository
  }

  internal fun getRule(ruleId: Int): SyntaxNodeDescriptor {
    val syntaxNodeDescriptor = rulesRepository?.getOrNull(ruleId)
    if (syntaxNodeDescriptor == null) {
      LOG.error { "Can't find syntax node by id: '$ruleId'" }
      return SyntaxNodeDescriptor.EMPTY_NODE
    }
    return syntaxNodeDescriptor
  }
}