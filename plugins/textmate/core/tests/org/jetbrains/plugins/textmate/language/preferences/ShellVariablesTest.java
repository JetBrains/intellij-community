package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.TestUtil;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.plist.CompositePlistReader;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.*;

public class ShellVariablesTest {
  @Test
  public void retrieveVariableValueBySelector() throws IOException {
    final ShellVariablesRegistry variablesRegistry = loadVariables(TestUtil.HTML);
    assertEquals("<!-- ", variablesRegistry.getVariableValue(Constants.COMMENT_START_VARIABLE, TestUtil.scopeFromString("text.html.basic")).value);
  }

  @Test
  public void retrieveVariableValueByUnmatchedSelector() throws IOException {
    final ShellVariablesRegistry preferencesRegistry = loadVariables(TestUtil.HTML);
    assertNull(preferencesRegistry.getVariableValue(Constants.COMMENT_START_VARIABLE, TestUtil.scopeFromString("text.unknown.basic")));
  }

  @NotNull
  private static ShellVariablesRegistry loadVariables(@NotNull String bundleName) throws IOException {
    final Bundle bundle = TestUtil.getBundle(bundleName);
    assertNotNull(bundle);
    final ShellVariablesRegistryImpl variablesRegistry = new ShellVariablesRegistryImpl();
    for (File file : bundle.getPreferenceFiles()) {
      for (Map.Entry<String, Plist> settingsPair : bundle.loadPreferenceFile(file, new CompositePlistReader())) {
        if (settingsPair != null) {
          variablesRegistry.fillVariablesFromPlist(settingsPair.getKey(), settingsPair.getValue());
        }
      }
    }
    return variablesRegistry;
  }
}
