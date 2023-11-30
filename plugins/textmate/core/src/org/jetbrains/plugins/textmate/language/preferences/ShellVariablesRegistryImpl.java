package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ShellVariablesRegistryImpl implements ShellVariablesRegistry {

  @NotNull private final Map<String, Collection<TextMateShellVariable>> myVariables = new ConcurrentHashMap<>();

  /**
   * Append table with new variables
   *
   * @deprecated use {@link this#addVariable(TextMateShellVariable)} instead
   */
  @Deprecated(forRemoval = true)
  public void fillVariablesFromPlist(@NotNull CharSequence scopeName, @NotNull Plist plist) {
    final PListValue shellVariables = plist.getPlistValue(Constants.SHELL_VARIABLES_KEY);
    if (shellVariables != null) {
      for (PListValue variable : shellVariables.getArray()) {
        Plist variablePlist = variable.getPlist();
        String name = variablePlist.getPlistValue(Constants.NAME_KEY, "").getString();
        String value = variablePlist.getPlistValue(Constants.VALUE_KEY, "").getString();
        addVariable(new TextMateShellVariable(scopeName, name, value));
      }
    }
  }

  public void addVariable(@NotNull TextMateShellVariable variable) {
    if (!variable.name.isEmpty()) {
      myVariables.computeIfAbsent(variable.name, key -> Collections.synchronizedList(new CopyOnWriteArrayList<>())).add(variable);
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
