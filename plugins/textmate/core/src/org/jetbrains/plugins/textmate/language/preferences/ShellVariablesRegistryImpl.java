package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ShellVariablesRegistryImpl implements ShellVariablesRegistry {

  @NotNull private final Map<String, Collection<TextMateShellVariable>> myVariables = new HashMap<>();

  /**
   * Append table with new variables
   */
  public void fillVariablesFromPlist(@NotNull CharSequence scopeName, @NotNull Plist plist) {
    final PListValue shellVariables = plist.getPlistValue(Constants.SHELL_VARIABLES_KEY);
    if (shellVariables != null) {
      for (PListValue variable : shellVariables.getArray()) {
        Plist variablePlist = variable.getPlist();
        String name = variablePlist.getPlistValue(Constants.NAME_KEY, "").getString();
        String value = variablePlist.getPlistValue(Constants.VALUE_KEY, "").getString();
        if (!name.isEmpty()) {
          myVariables.computeIfAbsent(name, (key) -> new ArrayList<>()).add(new TextMateShellVariable(scopeName, name, value));
        }
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
