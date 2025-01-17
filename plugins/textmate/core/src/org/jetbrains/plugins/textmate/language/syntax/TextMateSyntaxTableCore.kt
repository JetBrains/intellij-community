package org.jetbrains.plugins.textmate.language.syntax

import fleet.fastutil.ints.Int2ObjectOpenHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Table of textmate syntax rules.
 * Table represents mapping from scopeNames to set of syntax rules {@link SyntaxNodeDescriptor}.
 *
 * To lexing some file with this rule you should retrieve syntax rule
 * by scope name of target language {@link #getSyntax(CharSequence)}.
 *
 * Scope name of the target language can be found in syntax files of TextMate bundles.
 */
class TextMateSyntaxTableCore(
  private val rules: Map<CharSequence, SyntaxNodeDescriptor>,
  private val rulesRepository: Int2ObjectOpenHashMap<SyntaxNodeDescriptor>,
) {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(TextMateSyntaxTableCore::class.java)
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
    val syntaxNodeDescriptor = rules[scopeName]
    if (syntaxNodeDescriptor == null) {
      LOG.info("Can't find syntax node for scope: '{}'", scopeName)
      return SyntaxNodeDescriptor.EMPTY_NODE
    }
    return syntaxNodeDescriptor
  }

  internal fun getRule(ruleId: Int): SyntaxNodeDescriptor {
    val syntaxNodeDescriptor = rulesRepository[ruleId]
    if (syntaxNodeDescriptor == null) {
      LOG.error("Can't find syntax node by id: '{}'", ruleId)
      return SyntaxNodeDescriptor.EMPTY_NODE
    }
    return syntaxNodeDescriptor
  }
}