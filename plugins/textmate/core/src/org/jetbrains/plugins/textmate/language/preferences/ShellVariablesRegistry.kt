package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

interface ShellVariablesRegistry {
  /**
   * Returns variable value by scope selector.
   *
   * @param scope scope of current context.
   * @return preferences from table for given scope sorted by descending weigh
   * of rule selector relative to scope selector.
   */
  fun getVariableValue(name: String, scope: TextMateScope?): TextMateShellVariable?
}
