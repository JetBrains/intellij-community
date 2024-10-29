package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class ShellVariablesRegistryImpl implements ShellVariablesRegistry {

  private final Function<String, Collection<TextMateShellVariable>>
    ADD_VARIABLE_FACTORY = key -> Collections.synchronizedList(new CopyOnWriteArrayList<>());

  @NotNull private final Map<String, Collection<TextMateShellVariable>> myVariables = new ConcurrentHashMap<>();

  public void addVariable(@NotNull TextMateShellVariable variable) {
    if (!variable.name.isEmpty()) {
      myVariables.computeIfAbsent(variable.name, ADD_VARIABLE_FACTORY).add(variable);
    }
  }

  /**
   * Returns variable value by scope selector.
   *
   * @param scope scope of current context.
   * @return preferences from table for given scope sorted by descending weigh
   * of rule selector relative to scope selector.
   */
  @Override @Nullable
  public TextMateShellVariable getVariableValue(@NotNull String name, @Nullable TextMateScope scope) {
    if (scope == null) {
      return null;
    }
    Collection<TextMateShellVariable> variables = myVariables.get(name);
    if (variables == null) {
      return null;
    }
    return new TextMateScopeComparator<>(scope, TextMateShellVariable::getScopeSelector).max(variables);
  }

  public void clear() {
    myVariables.clear();
  }

}
