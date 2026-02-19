package org.jetbrains.plugins.textmate.language.preferences

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.plugins.textmate.language.TextMateScopeComparatorCore
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import org.jetbrains.plugins.textmate.update
import kotlin.concurrent.atomics.AtomicReference

class ShellVariablesRegistryBuilder(private val weigher: TextMateSelectorWeigher) {
  private val variables = AtomicReference(persistentMapOf<String, PersistentList<TextMateShellVariable>>())

  fun addVariable(variable: TextMateShellVariable) {
    if (variable.name.isNotEmpty()) {
      variables.update {
        it.put(variable.name, it[variable.name]?.add(variable) ?: persistentListOf(variable))
      }
    }
  }

  fun build(): ShellVariablesRegistry {
    return ShellVariablesRegistryImpl(weigher, variables.load())
  }
}

class ShellVariablesRegistryImpl(private val weigher: TextMateSelectorWeigher,
                                 private val variables: Map<String, List<TextMateShellVariable>>) : ShellVariablesRegistry {

  /**
   * Returns variable value by scope selector.
   *
   * @param scope scope of current context.
   * @return preferences from table for given scope sorted by descending weight
   * of rule selector relative to scope selector.
   */
  override fun getVariableValue(name: String, scope: TextMateScope?): TextMateShellVariable? {
    if (scope == null) {
      return null
    }
    val variables = variables[name] ?: return null
    return TextMateScopeComparatorCore(weigher, scope, TextMateShellVariable::scopeSelector).max(variables)
  }
}
