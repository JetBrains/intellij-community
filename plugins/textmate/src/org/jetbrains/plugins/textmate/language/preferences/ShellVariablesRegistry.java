package org.jetbrains.plugins.textmate.language.preferences;

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

public class ShellVariablesRegistry {
  @NotNull private final MultiMap<String, TextMateShellVariable> myVariables = MultiMap.create();

  /**
   * Append table with new variables
   */
  public void fillVariablesFromPlist(@NotNull String scopeName, @NotNull Plist plist) {
    final PListValue shellVariables = plist.getPlistValue(Constants.SHELL_VARIABLES_KEY);
    if (shellVariables != null) {
      for (PListValue variable : shellVariables.getArray()) {
        Plist variablePlist = variable.getPlist();
        String name = variablePlist.getPlistValue(Constants.NAME_KEY, "").getString();
        String value = variablePlist.getPlistValue(Constants.VALUE_KEY, "").getString();
        if (!name.isEmpty()) {
          myVariables.putValue(name, new TextMateShellVariable(scopeName, name, value));
        }
      }
    }
  }

  /**
   * Returns variable value by scope selector.
   *
   * @param scopeSelector selector of current context.
   * @return preferences from table for given scope sorted by descending weigh
   * of rule selector relative to scope selector.
   */
  @Nullable
  public TextMateShellVariable getVariableValue(@NotNull String name, @Nullable String scopeSelector) {
    if (scopeSelector == null) {
      return null;
    }
    return new TextMateScopeComparator<TextMateShellVariable>(scopeSelector).max(myVariables.get(name));
  }

  public void clear() {
    myVariables.clear();
  }
}
