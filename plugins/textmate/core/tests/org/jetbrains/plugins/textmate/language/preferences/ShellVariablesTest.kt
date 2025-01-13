package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.TestUtil;
import org.jetbrains.plugins.textmate.bundles.TextMatePreferences;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

public class ShellVariablesTest {
  @Test
  public void retrieveVariableValueBySelector() {
    final ShellVariablesRegistry variablesRegistry = loadVariables(TestUtil.HTML);
    assertEquals("<!-- ",
                 variablesRegistry.getVariableValue(Constants.COMMENT_START_VARIABLE, TestUtil.scopeFromString("text.html.basic")).value);
  }

  @Test
  public void retrieveVariableValueByUnmatchedSelector() {
    final ShellVariablesRegistry preferencesRegistry = loadVariables(TestUtil.HTML);
    assertNull(preferencesRegistry.getVariableValue(Constants.COMMENT_START_VARIABLE, TestUtil.scopeFromString("text.unknown.basic")));
  }

  @NotNull
  private static ShellVariablesRegistry loadVariables(@NotNull String bundleName) {
    Iterator<TextMatePreferences> preferences = TestUtil.readBundle(bundleName).readPreferences().iterator();
    assertNotNull(preferences);
    ShellVariablesRegistryImpl variablesRegistry = new ShellVariablesRegistryImpl(new TextMateSelectorWeigherImpl());
    while (preferences.hasNext()) {
      for (TextMateShellVariable variable : preferences.next().getVariables()) {
        variablesRegistry.addVariable(variable);
      }
    }
    return variablesRegistry;
  }
}
