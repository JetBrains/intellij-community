package org.jetbrains.plugins.textmate.language.preferences

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.plugins.textmate.language.TextMateScopeComparatorCore
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import java.util.concurrent.ConcurrentHashMap

class ShellVariablesRegistryImpl(private val weigher: TextMateSelectorWeigher) : ShellVariablesRegistry {
  private val myVariables: MutableMap<String?, PersistentList<TextMateShellVariable>> = ConcurrentHashMap<String?, PersistentList<TextMateShellVariable>>()

  fun addVariable(variable: TextMateShellVariable) {
    if (variable.name.isNotEmpty()) {
      myVariables.compute(variable.name) { k, v ->
        v?.add(variable) ?: persistentListOf(variable)
      }
    }
  }

  /**
   * Returns variable value by scope selector.
   *
   * @param scope scope of current context.
   * @return preferences from table for given scope sorted by descending weigh
   * of rule selector relative to scope selector.
   */
  override fun getVariableValue(name: String, scope: TextMateScope?): TextMateShellVariable? {
    if (scope == null) {
      return null
    }
    val variables = myVariables[name]
    if (variables == null) {
      return null
    }
    return TextMateScopeComparatorCore(weigher, scope, TextMateShellVariable::scopeSelector).max(variables)
  }

  fun clear() {
    myVariables.clear()
  }
}
